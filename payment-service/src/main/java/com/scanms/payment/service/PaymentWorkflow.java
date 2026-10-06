package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.entity.Payment;
import com.scanms.payment.exception.*;
import com.scanms.payment.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.security.SecureRandom;
import java.util.*;

@Service @RequiredArgsConstructor
public class PaymentWorkflow {
    private final PaymentRepository repository;
    private final PlatformTransactionManager manager;
    private final PaymentAccess access;
    private final FinanceService finance;
    private final PayosProvider provider;
    private final FinanceOutbox outbox;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;
    private <T> T tx(java.util.function.Supplier<T> work) { return new TransactionTemplate(manager).execute(s -> work.get()); }
    public PageView<PaymentView> history(int page,int size) {
        if(page<0 || size<1 || size>100) throw new AppException(ErrorCode.INVALID_REQUEST);
        var paging=org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC,"createdAt"));
        var rows=access.operator()?repository.findAll(paging):repository.findByPayerId(access.userId(),paging);
        return new PageView<>(rows.map(this::view).getContent(),rows.getTotalElements(),page,size);
    }
    public PaymentView create(PaymentInput input) {
        Map<String,Object> order=access.get(orderUrl+"/api/v1/orders/"+input.orderId());
        String user=access.userId();
        if(!Objects.equals(user,order.get("customerId"))) throw new AppException(ErrorCode.FORBIDDEN);
        var existing=repository.findByIdempotencyKey(input.idempotencyKey());
        if(existing.isPresent()) { same(existing.get(),"ORDER",input.orderId(),user,FinanceService.number(order,"payableVnd")); channel(existing.get(),"PAYOS",null); return link(existing.get()); }
        checkOrder(order);
        if(!provider.available()) throw new AppException(ErrorCode.CONFLICT,"Payment provider is not configured");
        if(repository.findByOrderIdOrderByCreatedAtDesc(input.orderId()).stream().anyMatch(p -> p.getStatus()==PaymentStatus.SUCCESS || p.getStatus()==PaymentStatus.PROCESSING || p.getStatus()==PaymentStatus.PENDING))
            throw new AppException(ErrorCode.CONFLICT,"Refresh or cancel the existing payment attempt first");
        Payment p=tx(() -> allocate("ORDER",input.orderId(),null,user,FinanceService.number(order,"payableVnd"),input.idempotencyKey()));
        return link(p);
    }
    public PaymentView topup(TopUpInput input) {
        var wallet=finance.ownedWallet(input.walletId());
        if(wallet.getOwnerType()!=WalletOwnerType.CUSTOMER) throw new AppException(ErrorCode.INVALID_REQUEST,"Only customer wallets support top-up");
        if(wallet.getStatus()==WalletStatus.CLOSED) throw new AppException(ErrorCode.CONFLICT,"Wallet is closed");
        String user=access.userId(); var existing=repository.findByIdempotencyKey(input.idempotencyKey());
        if(existing.isPresent()) { same(existing.get(),"TOP_UP",input.walletId(),user,input.amountVnd()); return link(existing.get()); }
        if(!provider.available()) throw new AppException(ErrorCode.CONFLICT,"Payment provider is not configured");
        return link(tx(() -> {
            var current=finance.lockOwnedWallet(input.walletId());
            if(current.getStatus()==WalletStatus.CLOSED) throw new AppException(ErrorCode.CONFLICT,"Wallet is closed");
            var concurrent=repository.findByIdempotencyKey(input.idempotencyKey());
            if(concurrent.isPresent()) { same(concurrent.get(),"TOP_UP",input.walletId(),user,input.amountVnd()); return concurrent.get(); }
            return allocate("TOP_UP",null,input.walletId(),user,input.amountVnd(),input.idempotencyKey());
        }));
    }
    public PaymentView payWallet(WalletPayInput input) {
        var wallet=finance.ownedWallet(input.walletId()); String user=access.userId();
        Map<String,Object> order=access.get(orderUrl+"/api/v1/orders/"+input.orderId());
        if(wallet.getOwnerType()!=WalletOwnerType.CUSTOMER || !Objects.equals(order.get("customerId"),user)) throw new AppException(ErrorCode.FORBIDDEN);
        return tx(() -> {
            var previous=repository.findByIdempotencyKey(input.idempotencyKey());
            if(previous.isPresent()) { same(previous.get(),"ORDER",input.orderId(),user,FinanceService.number(order,"payableVnd")); channel(previous.get(),"WALLET",input.walletId()); return view(previous.get()); }
            checkOrder(order);
            if(repository.findByOrderIdOrderByCreatedAtDesc(input.orderId()).stream().anyMatch(p -> p.getStatus()==PaymentStatus.SUCCESS || p.getStatus()==PaymentStatus.PROCESSING)) throw new AppException(ErrorCode.CONFLICT,"An existing payment is active");
            Payment payment=allocate("ORDER",input.orderId(),wallet.getWalletId(),user,FinanceService.number(order,"payableVnd"),input.idempotencyKey()); payment.setProviderCode("WALLET");
            var debit=finance.post(wallet.getWalletId(),WalletTransactionType.ORDER_PAYMENT,WalletTransactionDirection.DEBIT,payment.getAmountVnd(),"ORDER",input.orderId(),"order-payment:"+input.orderId());
            confirm(payment,debit.transactionId()); return view(repository.save(payment));
        });
    }
    private Payment allocate(String purpose,String order,String wallet,String user,long amount,String key) {
        if(amount<=0 || amount>9_000_000_000_000L) throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid payment amount");
        return repository.saveAndFlush(Payment.builder().orderId(order).activeOrderRef(order).walletId(wallet).payerId(user).purpose(purpose).amountVnd(amount).currency("VND")
                .providerCode("PAYOS").idempotencyKey(key).providerOrderCode(new SecureRandom().nextLong(1_000_000_000_000L,9_000_000_000_000L))
                .status(PaymentStatus.PROCESSING).expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)).build());
    }
    private void same(Payment p,String purpose,String reference,String user,long amount) {
        if(!Objects.equals(p.getPurpose(),purpose) || !Objects.equals(p.getPayerId(),user) || p.getAmountVnd()!=amount
                || !Objects.equals("TOP_UP".equals(purpose)?p.getWalletId():p.getOrderId(),reference)) throw new AppException(ErrorCode.CONFLICT,"Payment key belongs to another request");
    }
    private void channel(Payment p,String provider,String wallet) {
        if(!Objects.equals(p.getProviderCode(),provider) || !Objects.equals(p.getWalletId(),wallet))
            throw new AppException(ErrorCode.CONFLICT,"Payment key belongs to another payment method or wallet");
    }
    private void checkOrder(Map<String,Object> order) {
        if(!Set.of("PENDING","AWAITING_PAYMENT").contains(Objects.toString(order.get("status"))) || !"VND".equals(order.get("currency"))) throw new AppException(ErrorCode.CONFLICT,"Order cannot be paid");
        if(order.get("expiresAt")!=null && !LocalDateTime.parse(order.get("expiresAt").toString()).isAfter(LocalDateTime.now(ZoneOffset.UTC))) throw new AppException(ErrorCode.CONFLICT,"Order expired");
    }
    private PaymentView link(Payment original) {
        if(original.getCheckoutUrl()!=null || original.getStatus()==PaymentStatus.SUCCESS || original.getStatus()==PaymentStatus.CANCELLED) return view(original);
        Map<String,Object> data;
        try { data=provider.createLink(original); }
        catch(AppException | org.springframework.web.client.RestClientException ex) {
            // The first request may have succeeded remotely before its response was lost.
            // Always reconcile the same durable orderCode; never allocate another attempt here.
            data=provider.paymentStatus(original.getProviderOrderCode());
        }
        final Map<String,Object> result=data;
        return tx(() -> {
            Payment p=repository.lockById(original.getPaymentId()).orElseThrow();
            if(FinanceService.number(result,"orderCode")!=p.getProviderOrderCode() || FinanceService.number(result,"amount")!=p.getAmountVnd()) throw new AppException(ErrorCode.CONFLICT,"Provider link mismatch");
            if(result.get("checkoutUrl")!=null) p.setCheckoutUrl(result.get("checkoutUrl").toString());
            else if(result.get("id")!=null && result.get("id").toString().matches("[a-fA-F0-9]{32}"))
                p.setCheckoutUrl("https://pay.payos.vn/web/"+result.get("id"));
            if(result.get("qrCode")!=null) p.setQrCode(result.get("qrCode").toString());
            if(result.get("bin")!=null) p.setBin(result.get("bin").toString());
            if(result.get("accountNumber")!=null) p.setAccountNumber(result.get("accountNumber").toString());
            if(result.get("accountName")!=null) p.setAccountName(result.get("accountName").toString());
            if(result.get("description")!=null) p.setDescription(result.get("description").toString());
            if(p.getCheckoutUrl()==null && "PENDING".equals(result.get("status"))) {
                // Existing payment info doesn't include the link URL: keep the allocated reference for retry.
                p.setFailureReason("Refresh the provider link using the original payment reference");
            }
            applyProviderStatus(p,result); return view(repository.save(p));
        });
    }
    public PaymentView status(String id,boolean refresh) {
        Payment p=repository.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(!access.operator() && !Objects.equals(p.getPayerId(),access.userId())) throw new AppException(ErrorCode.FORBIDDEN);
        if(!refresh || !"PAYOS".equals(p.getProviderCode())) return view(p);
        Map<String,Object> status=provider.paymentStatus(p.getProviderOrderCode());
        return tx(() -> { Payment locked=repository.lockById(id).orElseThrow(); applyProviderStatus(locked,status); return view(repository.save(locked)); });
    }
    public PaymentView cancel(String id) {
        PaymentView allowed=status(id,false);
        if(allowed.status()==PaymentStatus.SUCCESS) throw new AppException(ErrorCode.CONFLICT,"Paid payments require refund");
        if(allowed.status()==PaymentStatus.CANCELLED) return allowed;
        Map<String,Object> result=provider.cancel(allowed.providerOrderCode());
        return tx(() -> { Payment p=repository.lockById(id).orElseThrow(); applyProviderStatus(p,result); return view(repository.save(p)); });
    }
    public PaymentView resume(String id) {
        var allowed=status(id,false);
        if(!Set.of(PaymentStatus.PENDING,PaymentStatus.PROCESSING).contains(allowed.status())) throw new AppException(ErrorCode.CONFLICT,"Only a pending provider payment can resume its link");
        Payment p=repository.findById(id).orElseThrow();
        if(!"PAYOS".equals(p.getProviderCode())) throw new AppException(ErrorCode.CONFLICT,"This payment has no provider checkout link");
        return link(p);
    }
    @SuppressWarnings("unchecked") public void webhook(Map<String,Object> envelope) {
        if(!(envelope.get("data") instanceof Map<?,?>)) throw new AppException(ErrorCode.INVALID_REQUEST);
        Map<String,Object> data=(Map<String,Object>)envelope.get("data"); provider.verifyWebhook(data,Objects.toString(envelope.get("signature"),null));
        if(!"00".equals(data.get("code"))) return;
        var match=repository.findByProviderOrderCode(FinanceService.number(data,"orderCode"));
        // Provider registration probes and unrelated references must not create local money.
        if(match.isEmpty()) return;
        if(!"VND".equals(data.get("currency"))) throw new AppException(ErrorCode.CONFLICT,"Provider currency mismatch");
        // A callback may describe one transfer rather than the accumulated link amount.
        // Query the provider when it differs; never infer received funds from an unsigned client request.
        boolean needsAggregate=FinanceService.number(data,"amount")!=match.get().getAmountVnd() || match.get().getStatus()==PaymentStatus.SUCCESS;
        Map<String,Object> aggregate=needsAggregate?provider.paymentStatus(match.get().getProviderOrderCode()):null;
        if(needsAggregate && (aggregate==null || aggregate.isEmpty())) throw new AppException(ErrorCode.CONFLICT,"Provider received funds cannot be verified");
        tx(() -> {
            Payment p=repository.lockById(match.get().getPaymentId()).orElseThrow();
            if(aggregate!=null) applyProviderStatus(p,aggregate);
            else confirm(p,Objects.toString(data.get("reference")));
            repository.save(p); return null;
        });
    }
    private void applyProviderStatus(Payment p,Map<String,Object> data) {
        if(FinanceService.number(data,"orderCode")!=p.getProviderOrderCode() || FinanceService.number(data,"amount")!=p.getAmountVnd()) throw new AppException(ErrorCode.CONFLICT,"Provider payment mismatch");
        String state=Objects.toString(data.get("status"));
        if("PAID".equals(state)) {
            long received=FinanceService.number(data,"amountPaid");
            if(received<p.getAmountVnd() || received>9_000_000_000_000L || (p.getReceivedAmountVnd()!=null && received<p.getReceivedAmountVnd())) throw new AppException(ErrorCode.CONFLICT,"Provider paid amount mismatch");
            p.setReceivedAmountVnd(received);
            confirm(p,Objects.toString(data.get("id"),p.getProviderOrderCode().toString()));
        } else if(p.getStatus()!=PaymentStatus.SUCCESS && ("CANCELLED".equals(state) || "EXPIRED".equals(state))) {
            p.setStatus("CANCELLED".equals(state)?PaymentStatus.CANCELLED:PaymentStatus.FAILED);
            p.setActiveOrderRef(null);
        }
    }
    private void confirm(Payment p,String reference) {
        if(p.getStatus()==PaymentStatus.SUCCESS) return;
        if(p.getReceivedAmountVnd()==null) p.setReceivedAmountVnd(p.getAmountVnd());
        p.setStatus(PaymentStatus.SUCCESS); p.setTransactionId(reference); p.setVerifiedAt(LocalDateTime.now(ZoneOffset.UTC)); p.setFailureReason(null);
        if("TOP_UP".equals(p.getPurpose())) finance.post(p.getWalletId(),WalletTransactionType.TOP_UP,WalletTransactionDirection.CREDIT,p.getAmountVnd(),"PAYMENT",p.getPaymentId(),"topup:"+p.getPaymentId());
        else { p.setOrderSyncStatus("PENDING"); outbox.enqueue("paid:"+p.getPaymentId(),orderUrl+"/api/v1/finance/orders/"+p.getOrderId()+"/paid",Map.of("paymentId",p.getPaymentId(),"amountVnd",p.getAmountVnd(),"currency","VND","verifiedAt",p.getVerifiedAt().toString(),"providerCode",p.getProviderCode(),"providerReference",Objects.toString(p.getTransactionId(),p.getPaymentId()))); }
    }
    public PaymentView refundUnapplied(String id,String reason) {
        access.operatorOnly();
        if(reason==null || reason.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST,"A reconciliation reason is required");
        return tx(() -> {
            Payment p=repository.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
            if("REFUNDED".equals(p.getOrderSyncStatus())) return view(p);
            if(p.getStatus()!=PaymentStatus.SUCCESS || !"ORDER".equals(p.getPurpose()) || !"RECONCILIATION_REQUIRED".equals(p.getOrderSyncStatus()))
                throw new AppException(ErrorCode.CONFLICT,"Only an unapplied verified order payment can be refunded");
            Map<String,Object> basis=access.get(orderUrl+"/api/v1/finance/orders/"+p.getOrderId()+"/unapplied-payment?paymentId="+p.getPaymentId());
            if(!Boolean.TRUE.equals(basis.get("refundable")) || !Objects.equals(basis.get("customerId"),p.getPayerId())
                    || !"VND".equals(basis.get("currency")) || FinanceService.number(basis,"amountVnd")!=p.getAmountVnd())
                throw new AppException(ErrorCode.CONFLICT,"Order cannot confirm an unapplied payment");
            var wallet=finance.ensureWallet(com.scanms.payment.constant.WalletOwnerType.CUSTOMER,p.getPayerId());
            long amount=Math.subtractExact(p.getReceivedAmountVnd()==null?p.getAmountVnd():p.getReceivedAmountVnd(),p.getSurplusRefundedAmountVnd()==null?0:p.getSurplusRefundedAmountVnd());
            var refund=finance.post(wallet.getWalletId(),WalletTransactionType.REFUND,WalletTransactionDirection.CREDIT,amount,"PAYMENT",id,"unapplied-payment:"+id);
            p.setResolutionAmountVnd(amount);
            p.setOrderSyncStatus("REFUNDED"); p.setResolutionReference(refund.transactionId()); p.setResolutionReason(reason); p.setResolvedBy(access.userId());
            repository.save(p); return view(p);
        });
    }
    public PaymentView refundSurplus(String id,String reason) {
        access.operatorOnly();
        if(reason==null || reason.isBlank() || reason.length()>500) throw new AppException(ErrorCode.INVALID_REQUEST,"A reconciliation reason is required");
        return tx(() -> {
            Payment p=repository.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
            if(p.getStatus()!=PaymentStatus.SUCCESS || p.getReceivedAmountVnd()==null || p.getReceivedAmountVnd()<p.getAmountVnd()) throw new AppException(ErrorCode.CONFLICT,"Verified received funds are required");
            long returned=p.getSurplusRefundedAmountVnd()==null?0:p.getSurplusRefundedAmountVnd();
            long applied="REFUNDED".equals(p.getOrderSyncStatus())?(p.getResolutionAmountVnd()==null?p.getAmountVnd():p.getResolutionAmountVnd()):p.getAmountVnd();
            long amount=Math.subtractExact(Math.subtractExact(p.getReceivedAmountVnd(),applied),returned);
            if(amount==0) return view(p);
            if(amount<0) throw new AppException(ErrorCode.CONFLICT,"Received/refunded snapshot is inconsistent");
            var wallet=finance.ensureWallet(WalletOwnerType.CUSTOMER,p.getPayerId());
            var refund=finance.post(wallet.getWalletId(),WalletTransactionType.REFUND,WalletTransactionDirection.CREDIT,amount,"PAYMENT_SURPLUS",id,"payment-surplus:"+id+":"+p.getReceivedAmountVnd());
            p.setSurplusRefundedAmountVnd(Math.addExact(returned,amount)); p.setSurplusReference(refund.transactionId());
            p.setResolutionReason(reason); p.setResolvedBy(access.userId()); return view(repository.save(p));
        });
    }
    public PaymentView view(Payment p) { return new PaymentView(p.getPaymentId(),p.getOrderId(),p.getPurpose(),p.getAmountVnd(),p.getCurrency(),p.getStatus(),p.getCheckoutUrl(),p.getQrCode(),p.getProviderOrderCode(),p.getFailureReason(),p.getVerifiedAt(),p.getExpiresAt(),p.getOrderSyncStatus(),p.getResolutionReference(),p.getReceivedAmountVnd(),p.getResolutionAmountVnd(),p.getSurplusRefundedAmountVnd(),p.getSurplusReference(),p.getBin(),p.getAccountNumber(),p.getAccountName(),p.getDescription()); }
}
