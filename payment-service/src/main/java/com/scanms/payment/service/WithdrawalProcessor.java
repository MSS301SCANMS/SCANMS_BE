package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.WithdrawalView;
import com.scanms.payment.entity.WithdrawalRequest;
import com.scanms.payment.exception.*;
import com.scanms.payment.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class WithdrawalProcessor {
    private final WithdrawalRequestRepository repository;
    private final FinanceService finance;
    private final PaymentAccess access;
    private final PayosProvider provider;
    private final BankCipher cipher;
    private final BankBusinessCalendar calendar;
    private final PlatformTransactionManager manager;
    private <T> T tx(java.util.function.Supplier<T> work) { return new TransactionTemplate(manager).execute(s -> work.get()); }
    @SuppressWarnings("unchecked") public WithdrawalView execute(String id) {
        access.trustedOnly();
        WithdrawalRequest request=tx(() -> {
            WithdrawalRequest w=finance.lockedWithdrawal(id);
            if(w.getStatus()==WithdrawalStatus.SUCCESS) return w;
            if(w.getStatus()!=WithdrawalStatus.APPROVED && w.getStatus()!=WithdrawalStatus.PROCESSING) throw new AppException(ErrorCode.CONFLICT,"Withdrawal must be approved first");
            if(w.getStatus()==WithdrawalStatus.APPROVED && w.getScheduledFor()!=null && w.getScheduledFor().isAfter(Instant.now())) throw new AppException(ErrorCode.CONFLICT,"Transfer is scheduled for the next business day");
            if(!provider.payoutAvailable()) throw new AppException(ErrorCode.CONFLICT,"Payout provider is not configured");
            w.setStatus(WithdrawalStatus.PROCESSING); w.setProcessedAt(Instant.now()); return repository.saveAndFlush(w);
        });
        if(request.getStatus()==WithdrawalStatus.SUCCESS) return finance.withdrawalView(request);
        try {
            Map<String,Object> dest=tools.jackson.databind.json.JsonMapper.builder().build().readValue(request.getDestinationSnapshot(),Map.class);
            Map<String,Object> result=request.getProviderReference()!=null?provider.payoutStatus(request.getProviderReference()):provider.findPayout(id);
            if(result.isEmpty()) {
                Instant now=Instant.now(), candidate=calendar.nextProcessingAt(now);
                Instant next=request.getScheduledFor()!=null && request.getScheduledFor().isAfter(candidate)?request.getScheduledFor():candidate;
                if(next.isAfter(now)) return tx(() -> {
                    var w=finance.lockedWithdrawal(id);
                    if(w.getStatus()==WithdrawalStatus.PROCESSING) { w.setScheduledFor(next); w.setFailureReason("Awaiting the next bank business day; funds remain held"); }
                    return finance.withdrawalView(repository.save(w));
                });
                result=provider.transfer(id,request.getNetAmountVnd(),dest.get("bankCode").toString(),cipher.decrypt(dest.get("accountCiphertext").toString()));
            }
            return verified(id,result);
        } catch(Exception ex) {
            // Network/unknown result must retain the hold, even if the provider may already have paid.
            return tx(() -> { var w=finance.lockedWithdrawal(id);
                if(w.getStatus()==WithdrawalStatus.PROCESSING) w.setFailureReason("Provider result unknown; reconcile before retrying or releasing funds");
                return finance.withdrawalView(repository.save(w)); });
        }
    }
    public WithdrawalView reconcile(String id) { return execute(id); }
    /** Signed notification only wakes reconciliation; its claimed state/amount never posts money. */
    public WithdrawalView notification(Map<String,Object> payload,String signature) {
        provider.verifyPayoutNotification(payload,signature);
        String reference=Objects.toString(payload.get("referenceId"),"");
        if(reference.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST,"Withdrawal reference required");
        WithdrawalRequest request=repository.findById(reference).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(request.getProviderReference()==null || !Objects.equals(request.getProviderReference(),payload.get("id")))
            throw new AppException(ErrorCode.CONFLICT,"Payout notification identity mismatch");
        if(request.getStatus()==WithdrawalStatus.SUCCESS || request.getStatus()==WithdrawalStatus.FAILED) return finance.withdrawalView(request);
        if(request.getStatus()!=WithdrawalStatus.PROCESSING) throw new AppException(ErrorCode.CONFLICT,"Withdrawal has not been sent to provider");
        // Lookup only. A callback can never initiate a bank transfer or bypass the business calendar.
        return verified(reference,provider.payoutStatus(request.getProviderReference()));
    }
    private WithdrawalView verified(String id,Map<String,Object> result) {
        return tx(() -> {
            WithdrawalRequest w=finance.lockedWithdrawal(id);
            if(w.getStatus()==WithdrawalStatus.SUCCESS || w.getStatus()==WithdrawalStatus.FAILED) return finance.withdrawalView(w);
            if(!id.equals(result.get("referenceId"))) throw new AppException(ErrorCode.CONFLICT,"Payout reference mismatch");
            if(!(result.get("id") instanceof String providerId) || providerId.isBlank()) throw new AppException(ErrorCode.CONFLICT,"Provider payout id missing");
            w.setProviderReference(Objects.toString(result.get("id"))); w.setFailureReason(null);
            List<Map<?,?>> transactions=new ArrayList<>(); Object rows=result.get("transactions");
            if(rows instanceof List<?> list) for(Object row:list) if(row instanceof Map<?,?> m) transactions.add(m);
            if(rows instanceof Map<?,?> map) for(Object row:map.values()) if(row instanceof Map<?,?> m) transactions.add(m);
            if(transactions.size()!=1) throw new AppException(ErrorCode.CONFLICT,"Unexpected payout transaction count");
            Map<?,?> transaction=transactions.getFirst();
            if(new java.math.BigDecimal(Objects.toString(transaction.get("amount"))).longValueExact()!=w.getNetAmountVnd()) throw new AppException(ErrorCode.CONFLICT,"Payout amount mismatch");
            @SuppressWarnings("unchecked") Map<String,Object> dest=tools.jackson.databind.json.JsonMapper.builder().build().readValue(w.getDestinationSnapshot(),Map.class);
            if(!Objects.equals(transaction.get("toBin"),dest.get("bankCode")) || !Objects.equals(transaction.get("toAccountNumber"),cipher.decrypt(dest.get("accountCiphertext").toString()))) throw new AppException(ErrorCode.CONFLICT,"Payout destination mismatch");
            String state=Objects.toString(transaction.get("state"));
            if("SUCCEEDED".equals(state)) {
                finance.post(w.getWalletId(),WalletTransactionType.WITHDRAWAL,WalletTransactionDirection.DEBIT,w.getAmountVnd(),"WITHDRAWAL",id,"withdrawal:"+id+":paid");
                if(w.getFeeVnd()>0) {
                    var platform=finance.ensureWallet(WalletOwnerType.PLATFORM,"SCANMS");
                    finance.post(platform.getWalletId(),WalletTransactionType.PLATFORM_FEE,WalletTransactionDirection.CREDIT,w.getFeeVnd(),"WITHDRAWAL",id,"withdrawal:"+id+":fee");
                }
                w.setStatus(WithdrawalStatus.SUCCESS); w.setCompletedAt(Instant.now());
            } else if("FAILED".equals(state) || "CANCELLED".equals(state)) {
                finance.post(w.getWalletId(),WalletTransactionType.RELEASE,WalletTransactionDirection.CREDIT,w.getAmountVnd(),"WITHDRAWAL",id,"withdrawal:"+id+":release");
                w.setStatus(WithdrawalStatus.FAILED); w.setFailureReason("Provider confirmed transfer failure"); w.setCompletedAt(Instant.now());
            }
            return finance.withdrawalView(repository.save(w));
        });
    }
}
