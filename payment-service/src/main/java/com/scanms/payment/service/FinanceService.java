package com.scanms.payment.service;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.*;
import com.scanms.payment.entity.*;
import com.scanms.payment.exception.*;
import com.scanms.payment.mapper.FeeConfigMapper;
import com.scanms.payment.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Transactional
public class FinanceService {
    private final WalletRepository wallets;
    private final WalletTransactionRepository entries;
    private final WalletTransactionService ledger;
    private final BankAccountRepository banks;
    private final WithdrawalRequestRepository withdrawals;
    private final SellerSettlementRepository settlements;
    private final FeeConfigRepository fees;
    private final FeeConfigMapper feeMapper;
    private final PaymentAccess access;
    private final FeeCalculator calculator;
    private final BankCipher cipher;
    private final BankBusinessCalendar calendar;
    private final FinanceOutbox outbox;
    private final PaymentRepository payments;
    private final jakarta.persistence.EntityManager entityManager;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;
    @Value("${clients.promotion.url:http://localhost:8085}") private String promotionUrl;
    @Value("${scanms.payment.minimum-withdrawal-vnd:200000}") private long minimum;

    public Wallet ensureWallet(WalletOwnerType type,String owner) {
        return wallets.findByOwnerTypeAndOwnerRefIdAndCurrency(type,owner,"VND").orElseGet(() -> wallets.saveAndFlush(Wallet.builder()
                .ownerType(type).ownerRefId(owner).currency("VND").availableBalanceVnd(0L).heldBalanceVnd(0L).status(WalletStatus.ACTIVE).build()));
    }
    public WalletView myWallet(WalletOwnerType type,String storeId) {
        String owner=switch(type) { case CUSTOMER -> access.userId(); case COLLABORATOR -> access.collaboratorId();
            case STORE -> { if(storeId==null || storeId.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST,"Choose a store"); yield storeId; }
            case PLATFORM -> "SCANMS"; };
        access.owner(type,owner); return walletView(ensureWallet(type,owner));
    }
    public Wallet ownedWallet(String id) {
        Wallet wallet=wallets.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Wallet not found"));
        access.wallet(wallet); return wallet;
    }
    public WalletView wallet(String id) { return walletView(ownedWallet(id)); }
    public Wallet lockOwnedWallet(String id) {
        Wallet w=ownedWallet(id); entityManager.refresh(w,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return w;
    }
    public Map<String,Object> capabilities() { return Map.of("minimumWithdrawalVnd",minimum,"currency","VND"); }
    @Transactional(readOnly=true) public PageView<LedgerView> history(String id,int page,int size) {
        return history(id,page,size,null,null,null,null);
    }
    @Transactional(readOnly=true) public PageView<LedgerView> history(String id,int page,int size,WalletTransactionType type,Instant from,Instant to,String reference) {
        ownedWallet(id);
        if(from!=null && to!=null && from.isAfter(to)) throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid date range");
        var results=entries.findAll((root,query,cb) -> {
            var predicates=new ArrayList<jakarta.persistence.criteria.Predicate>(); predicates.add(cb.equal(root.get("walletId"),id));
            if(type!=null) predicates.add(cb.equal(root.get("type"),type));
            if(from!=null) predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"),from));
            if(to!=null) predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"),to));
            if(reference!=null && !reference.isBlank()) predicates.add(cb.equal(root.get("referenceId"),reference));
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },paging(page,size,"createdAt"));
        return new PageView<>(results.stream().map(e -> new LedgerView(e.getTransactionId(),e.getType(),e.getDirection(),e.getAmountVnd(),
                e.getBalanceBeforeVnd(),e.getBalanceAfterVnd(),e.getHeldBeforeVnd(),e.getHeldAfterVnd(),e.getReferenceType(),e.getReferenceId(),e.getCreatedAt())).toList(),results.getTotalElements(),page,size);
    }
    public WalletTransactionResponse transaction(String walletId,String transactionId) {
        ownedWallet(walletId); var entry=entries.findById(transactionId).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(!walletId.equals(entry.getWalletId())) throw new AppException(ErrorCode.RESOURCE_NOT_FOUND);
        return ledger.getById(transactionId);
    }
    public PageView<WalletView> walletList(WalletOwnerType type,String owner,int page,int size) {
        access.operatorOnly();
        var result=wallets.findAll((root,query,cb) -> {
            var p=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if(type!=null) p.add(cb.equal(root.get("ownerType"),type));
            if(owner!=null && !owner.isBlank()) p.add(cb.equal(root.get("ownerRefId"),owner));
            return cb.and(p.toArray(jakarta.persistence.criteria.Predicate[]::new));
        },paging(page,size,"createdAt"));
        return new PageView<>(result.stream().map(this::walletView).toList(),result.getTotalElements(),page,size);
    }
    public WalletView walletStatus(String id,WalletStatus status) {
        access.operatorOnly(); Wallet wallet=lockedWallet(id);
        if(status==WalletStatus.CLOSED && (wallet.getAvailableBalanceVnd()!=0 || wallet.getHeldBalanceVnd()!=0)) throw new AppException(ErrorCode.CONFLICT,"A funded wallet cannot be closed");
        if(status==WalletStatus.CLOSED && payments.existsByWalletIdAndStatusIn(id,List.of(PaymentStatus.PROCESSING,PaymentStatus.PENDING)))
            throw new AppException(ErrorCode.CONFLICT,"Reconcile pending wallet payments before closing the wallet");
        wallet.setStatus(status); return walletView(wallets.save(wallet));
    }
    public BankView registerBank(BankInput input) {
        if(input.ownerType()!=WalletOwnerType.STORE && input.ownerType()!=WalletOwnerType.COLLABORATOR) throw new AppException(ErrorCode.INVALID_REQUEST,"Only store/collaborator withdrawal destinations are supported");
        access.owner(input.ownerType(),input.ownerRefId());
        return createBank(input);
    }
    private BankView createBank(BankInput input) {
        BankAccount bank=BankAccount.builder().storeId(input.ownerType()==WalletOwnerType.STORE?input.ownerRefId():null)
                .collaboratorId(input.ownerType()==WalletOwnerType.COLLABORATOR?input.ownerRefId():null).bankCode(input.bankCode())
                .holderName(input.holderName().trim().toUpperCase(Locale.ROOT)).accountCiphertext(cipher.encrypt(input.accountNumber()))
                .maskedNumber("••••"+input.accountNumber().substring(input.accountNumber().length()-4)).verificationStatus(BankAccountVerificationStatus.PENDING).active(true).build();
        return bankView(banks.save(bank));
    }
    @Transactional(readOnly=true) public List<BankView> bankList(WalletOwnerType type,String owner) {
        if(type==null) { access.operatorOnly(); return banks.findAll().stream().map(this::bankView).toList(); }
        if((type!=WalletOwnerType.STORE && type!=WalletOwnerType.COLLABORATOR) || owner==null || owner.isBlank())
            throw new AppException(ErrorCode.INVALID_REQUEST,"Choose a store or collaborator owner");
        access.owner(type,owner);
        return (type==WalletOwnerType.STORE?banks.findByStoreId(owner):banks.findByCollaboratorId(owner)).stream().map(this::bankView).toList();
    }
    public BankView bankStatus(String id,BankAccountVerificationStatus status) {
        access.operatorOnly(); BankAccount b=bank(id); entityManager.refresh(b,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if(!Boolean.TRUE.equals(b.getActive())) throw new AppException(ErrorCode.CONFLICT,"An inactive bank account cannot be verified");
        b.setVerificationStatus(status); b.setVerifiedBy(org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication()==null?"operator":org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName()); b.setVerifiedAt(Instant.now()); return bankView(banks.save(b));
    }
    public BankView replaceBank(String id,BankInput input) {
        BankAccount old=bank(id); entityManager.refresh(old,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        access.owner(input.ownerType(),input.ownerRefId());
        if(!Objects.equals(input.ownerType()==WalletOwnerType.STORE?old.getStoreId():old.getCollaboratorId(),input.ownerRefId())) throw new AppException(ErrorCode.FORBIDDEN);
        var previous=banks.findByReplacesBankId(id);
        if(previous.isPresent()) { sameBank(previous.get(),input); return bankView(previous.get()); }
        if(!Boolean.TRUE.equals(old.getActive())) throw new AppException(ErrorCode.CONFLICT,"Bank account is already inactive");
        var replacement=bank(createBank(input).bankAccountId()); replacement.setReplacesBankId(id); old.setActive(false); banks.save(old); return bankView(banks.save(replacement));
    }
    public BankView syncKycBank(KycBankInput input) {
        if(input.bankOwnershipVerified() && !access.role("KYC_INTERNAL")) throw new AppException(ErrorCode.FORBIDDEN,"Only the KYC service may attest verified bank ownership");
        if(!access.role("KYC_INTERNAL")) access.owner(input.bank().ownerType(),input.bank().ownerRefId());
        if(input.bank().ownerType()!=WalletOwnerType.STORE && input.bank().ownerType()!=WalletOwnerType.COLLABORATOR) throw new AppException(ErrorCode.INVALID_REQUEST);
        String ref=input.bank().ownerType()+":"+input.bank().ownerRefId()+":"+input.caseReference();
        var previous=banks.findBySourceKycReference(ref);
        BankAccount b;
        if(previous.isPresent()) { b=previous.get(); sameBank(b,input.bank()); }
        else { b=bank(createBank(input.bank()).bankAccountId()); b.setSourceKycReference(ref); }
        if(input.bankOwnershipVerified()) { b.setVerificationStatus(BankAccountVerificationStatus.VERIFIED); b.setVerifiedAt(Instant.now()); b.setVerifiedBy("KYC:"+input.caseReference()); }
        return bankView(banks.saveAndFlush(b));
    }
    private void sameBank(BankAccount b,BankInput input) {
        if(!Boolean.TRUE.equals(b.getActive()) || !Objects.equals(b.getBankCode(),input.bankCode()) || !Objects.equals(b.getHolderName(),input.holderName().trim().toUpperCase(Locale.ROOT)) || !Objects.equals(cipher.decrypt(b.getAccountCiphertext()),input.accountNumber())) throw new AppException(ErrorCode.CONFLICT,"Bank reference belongs to changed details; submit a new case or replacement");
    }
    public BankView deactivateBank(String id) {
        BankAccount b=bank(id); access.owner(b.getStoreId()!=null?WalletOwnerType.STORE:WalletOwnerType.COLLABORATOR,b.getStoreId()!=null?b.getStoreId():b.getCollaboratorId());
        b.setActive(false); return bankView(banks.save(b));
    }
    private BankAccount bank(String id) { return banks.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,"Bank account not found")); }
    private void destination(Wallet wallet,BankAccount bank) {
        boolean matches=wallet.getOwnerType()==WalletOwnerType.STORE?Objects.equals(wallet.getOwnerRefId(),bank.getStoreId()):
                wallet.getOwnerType()==WalletOwnerType.COLLABORATOR && Objects.equals(wallet.getOwnerRefId(),bank.getCollaboratorId());
        if(!matches || !Boolean.TRUE.equals(bank.getActive()) || bank.getVerificationStatus()!=BankAccountVerificationStatus.VERIFIED)
            throw new AppException(ErrorCode.CONFLICT,"Verified bank account must belong to the wallet owner");
    }
    public WithdrawalView requestWithdrawal(WithdrawalInput input) {
        Wallet wallet=ownedWallet(input.walletId());
        entityManager.refresh(wallet,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        var previous=withdrawals.findByIdempotencyKey(input.idempotencyKey());
        if(previous.isPresent()) {
            WithdrawalRequest old=previous.get();
            if(!Objects.equals(old.getWalletId(),input.walletId()) || !Objects.equals(old.getBankAccountId(),input.bankAccountId()) || !Objects.equals(old.getAmountVnd(),input.amountVnd()))
                throw new AppException(ErrorCode.CONFLICT,"Withdrawal key belongs to another request");
            return withdrawalView(old);
        }
        if(wallet.getStatus()!=WalletStatus.ACTIVE || input.amountVnd()<minimum || input.amountVnd()>wallet.getAvailableBalanceVnd()) throw new AppException(ErrorCode.CONFLICT,"Insufficient available balance or amount below minimum");
        BankAccount bank=bank(input.bankAccountId()); destination(wallet,bank);
        var fee=calculator.quote(FeeType.WITHDRAWAL_FEE,input.amountVnd(),Instant.now());
        if(fee.amountVnd()>=input.amountVnd()) throw new AppException(ErrorCode.CONFLICT,"Withdrawal fee exceeds amount");
        WithdrawalRequest request=WithdrawalRequest.builder().walletId(wallet.getWalletId()).bankAccountId(bank.getBankAccountId())
                .amountVnd(input.amountVnd()).idempotencyKey(input.idempotencyKey()).status(WithdrawalStatus.REQUESTED)
                .feeVnd(fee.amountVnd()).netAmountVnd(input.amountVnd()-fee.amountVnd()).feeSnapshot(json(fee.snapshot()))
                .destinationSnapshot(json(Map.of("bankCode",bank.getBankCode(),"accountCiphertext",bank.getAccountCiphertext(),"holderName",bank.getHolderName()))).build();
        return withdrawalView(withdrawals.saveAndFlush(request));
    }
    public WithdrawalView approveWithdrawal(String id) {
        access.operatorOnly(); WithdrawalRequest w=lockedWithdrawal(id);
        if(w.getStatus()==WithdrawalStatus.APPROVED || w.getStatus()==WithdrawalStatus.PROCESSING || w.getStatus()==WithdrawalStatus.SUCCESS) return withdrawalView(w);
        if(w.getStatus()!=WithdrawalStatus.REQUESTED) throw new AppException(ErrorCode.CONFLICT,"Request is not awaiting approval");
        Wallet wallet=ownedWallet(w.getWalletId()); destination(wallet,bank(w.getBankAccountId()));
        post(wallet.getWalletId(),WalletTransactionType.HOLD,WalletTransactionDirection.DEBIT,w.getAmountVnd(),"WITHDRAWAL",id,"withdrawal:"+id+":hold");
        w.setStatus(WithdrawalStatus.APPROVED); w.setApprovedAt(Instant.now()); w.setScheduledFor(calendar.nextProcessingAt(Instant.now()));
        return withdrawalView(withdrawals.save(w));
    }
    public WithdrawalView rejectWithdrawal(String id,String reason,boolean cancel) {
        WithdrawalRequest w=lockedWithdrawal(id); ownedWallet(w.getWalletId());
        if(!cancel) access.operatorOnly();
        WithdrawalStatus target=cancel?WithdrawalStatus.CANCELLED:WithdrawalStatus.REJECTED;
        if(w.getStatus()==target) return withdrawalView(w);
        if(w.getStatus()!=WithdrawalStatus.REQUESTED && (!access.operator() || w.getStatus()!=WithdrawalStatus.APPROVED)) throw new AppException(ErrorCode.CONFLICT,"A transfer in progress must be reconciled first");
        if(w.getStatus()==WithdrawalStatus.APPROVED) post(w.getWalletId(),WalletTransactionType.RELEASE,WalletTransactionDirection.CREDIT,w.getAmountVnd(),"WITHDRAWAL",id,"withdrawal:"+id+":release");
        w.setStatus(target); w.setFailureReason(reason); w.setCompletedAt(Instant.now()); return withdrawalView(withdrawals.save(w));
    }
    public WithdrawalRequest lockedWithdrawal(String id) { return withdrawals.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND)); }
    private Wallet lockedWallet(String id) {
        Wallet w=wallets.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        entityManager.refresh(w,jakarta.persistence.LockModeType.PESSIMISTIC_WRITE); return w;
    }
    @Transactional(readOnly=true) public PageView<WithdrawalView> withdrawalList(String walletId,int page,int size) {
        Page<WithdrawalRequest> result;
        if(walletId==null) { access.operatorOnly(); result=withdrawals.findAll(paging(page,size,"requestedAt")); }
        else { ownedWallet(walletId); result=withdrawals.findByWalletId(walletId,paging(page,size,"requestedAt")); }
        return new PageView<>(result.stream().map(this::withdrawalView).toList(),result.getTotalElements(),page,size);
    }
    public WalletTransactionResponse refund(String id) {
        access.trustedOnly();
        var previous=entries.findByIdempotencyKey("refund:"+id);
        if(previous.isPresent()) {
            var old=previous.get();
            if(old.getType()!=WalletTransactionType.REFUND || !"RETURN".equals(old.getReferenceType()) || !id.equals(old.getReferenceId())) throw new AppException(ErrorCode.CONFLICT,"Refund key belongs to another ledger command");
            return ledger.getById(old.getTransactionId());
        }
        Map<String,Object> source=access.get(orderUrl+"/api/v1/finance/refund-basis/"+id);
        if(!"VND".equals(source.get("currency")) || !id.equals(source.get("returnRequestId"))
                || Objects.toString(source.get("orderItemId"),"").isBlank() || Objects.toString(source.get("customerId"),"").isBlank())
            throw new AppException(ErrorCode.CONFLICT,"Refund basis identity or currency mismatch");
        long amount=number(source,"amountVnd"); Wallet wallet=ensureWallet(WalletOwnerType.CUSTOMER,source.get("customerId").toString());
        var result=post(wallet.getWalletId(),WalletTransactionType.REFUND,WalletTransactionDirection.CREDIT,amount,"RETURN",id,"refund:"+id);
        outbox.enqueue("refund:"+id,orderUrl+"/api/v1/finance/returns/"+id+"/refunded",Map.of("transactionId",result.transactionId(),"amountVnd",amount));
        return result;
    }
    public WalletTransactionResponse refundResult(String id) {
        var entry=entries.findByIdempotencyKey("refund:"+id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        ownedWallet(entry.getWalletId()); return ledger.getById(entry.getTransactionId());
    }
    public WalletTransactionResponse commission(String id) {
        access.trustedOnly(); Map<String,Object> source=access.get(promotionUrl+"/api/v1/finance/commission-basis/"+id);
        long amount=number(source,"amountVnd"); Wallet wallet=ensureWallet(WalletOwnerType.COLLABORATOR,source.get("collaboratorId").toString());
        var result=post(wallet.getWalletId(),WalletTransactionType.KOL_COMMISSION,WalletTransactionDirection.CREDIT,amount,"COMMISSION",id,"commission:"+id);
        outbox.enqueue("commission:"+id,promotionUrl+"/api/v1/finance/commissions/"+id+"/credited",Map.of("transactionId",result.transactionId(),"amountVnd",amount));
        return result;
    }
    public SettlementView computeSettlement(String sellerOrderId) {
        return computeSettlement(sellerOrderId,false);
    }
    private SettlementView computeSettlement(String sellerOrderId,boolean reconcile) {
        access.trustedOnly(); var old=settlements.lockBySellerOrderId(sellerOrderId);
        if(!reconcile && old.isPresent() && old.get().getStatus()==SettlementStatus.PAID) return settlementView(old.get());
        Map<String,Object> source=access.get(orderUrl+"/api/v1/finance/settlement-basis/"+sellerOrderId);
        String storeId=source.get("storeId").toString();
        String ids=Objects.toString(source.get("orderItemIds"),"");
        Map<String,Object> comm=access.get(promotionUrl+"/api/v1/finance/commissions-total?orderItemIds="+ids);
        if(!Boolean.TRUE.equals(comm.get("finalized"))) throw new AppException(ErrorCode.CONFLICT,"Affiliate has not finalized all commission calculations");
        if(old.isPresent() && !reconcile) {
            Map<String,Object> snapshot=old.get().getBreakdown();
            for(String key:List.of("grossRevenueVnd","refundVnd","sellerDiscountVnd","platformSubsidyVnd","feeBasisVnd"))
                if(number(snapshot,key)!=number(source,key)) throw new AppException(ErrorCode.CONFLICT,"Settlement source changed; reconcile the existing snapshot");
            if(number(snapshot,"commissionVnd")!=number(comm,"amountVnd")) throw new AppException(ErrorCode.CONFLICT,"Settlement commission changed; reconcile first");
            return settlementView(old.get());
        }
        long basis=number(source,"feeBasisVnd"); Instant at=Instant.now();
        var platform=old.isPresent()?calculator.requote(snapshotMap(old.get().getBreakdown(),"platformFee"),basis):calculator.quote(FeeType.PLATFORM_FEE,basis,at);
        var service=old.isPresent()?calculator.requote(snapshotMap(old.get().getBreakdown(),"serviceFee"),basis):calculator.quote(FeeType.SERVICE_FEE,basis,at);
        long total;
        try {
            total=Math.subtractExact(number(source,"grossRevenueVnd"),number(source,"refundVnd"));
            total=Math.subtractExact(total,number(source,"sellerDiscountVnd"));
            total=Math.subtractExact(total,platform.amountVnd()); total=Math.subtractExact(total,service.amountVnd());
            total=Math.subtractExact(total,number(comm,"amountVnd")); total=Math.addExact(total,number(source,"platformSubsidyVnd"));
        } catch(ArithmeticException ex) { throw new AppException(ErrorCode.CONFLICT,"Settlement amount overflow"); }
        if(total<0) throw new AppException(ErrorCode.CONFLICT,"Settlement is negative; reconcile its source data");
        Map<String,Object> breakdown=new LinkedHashMap<>(source); breakdown.put("platformFee",platform.snapshot()); breakdown.put("serviceFee",service.snapshot());
        breakdown.put("commissionVnd",number(comm,"amountVnd")); breakdown.put("netAmountVnd",total);
        breakdown.put("platformFeeVnd",platform.amountVnd()); breakdown.put("serviceFeeVnd",service.amountVnd());
        SellerSettlement settlement=old.orElseGet(SellerSettlement::new);
        settlement.setSellerOrderId(sellerOrderId); settlement.setStoreId(storeId); settlement.setBreakdown(breakdown);
        settlement.setNetAmountVnd(total); settlement.setStatus(SettlementStatus.ELIGIBLE); settlement.setOrderRefs(Map.of("sellerOrderId",sellerOrderId,"orderItemIds",ids));
        return settlementView(settlements.saveAndFlush(settlement));
    }
    public SettlementView reconcileSettlement(String id,String reason) {
        access.operatorOnly();
        if(reason==null || reason.isBlank()) throw new AppException(ErrorCode.INVALID_REQUEST,"A reconciliation reason is required");
        SellerSettlement s=settlements.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        boolean paid=s.getStatus()==SettlementStatus.PAID; long before=s.getNetAmountVnd();
        long beforeFees=Math.addExact(number(s.getBreakdown(),"platformFeeVnd"),number(s.getBreakdown(),"serviceFeeVnd"));
        Map<String,Object> history=new LinkedHashMap<>(s.getReconciliationHistory()==null?Map.of():s.getReconciliationHistory());
        String revision=Integer.toString(history.size()+1);
        history.put(revision,Map.of("before",new LinkedHashMap<>(s.getBreakdown()),"reason",reason,"actor",access.userId(),"at",Instant.now().toString(),"wasPaid",paid));
        s.setReconciliationHistory(history);
        computeSettlement(s.getSellerOrderId(),true);
        if(paid) {
            long delta=Math.subtractExact(s.getNetAmountVnd(),before);
            if(delta!=0) post(s.getWalletId(),WalletTransactionType.ADJUSTMENT,delta>0?WalletTransactionDirection.CREDIT:WalletTransactionDirection.DEBIT,Math.abs(delta),"SETTLEMENT_ADJUSTMENT",id,"settlement:"+id+":adjust:"+revision);
            long feesDelta=Math.subtractExact(Math.addExact(number(s.getBreakdown(),"platformFeeVnd"),number(s.getBreakdown(),"serviceFeeVnd")),beforeFees);
            if(feesDelta!=0) post(ensureWallet(WalletOwnerType.PLATFORM,"SCANMS").getWalletId(),WalletTransactionType.ADJUSTMENT,feesDelta>0?WalletTransactionDirection.CREDIT:WalletTransactionDirection.DEBIT,Math.abs(feesDelta),"SETTLEMENT_ADJUSTMENT",id,"settlement:"+id+":fees-adjust:"+revision);
            s.setStatus(SettlementStatus.PAID);
        }
        return settlementView(settlements.save(s));
    }
    public SettlementView creditSettlement(String id) {
        access.operatorOnly(); SellerSettlement s=settlements.lockById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(s.getStatus()==SettlementStatus.PAID) return settlementView(s);
        computeSettlement(s.getSellerOrderId()); // Revalidate immutable eligibility and final adjustments at approval.
        Wallet wallet=ensureWallet(WalletOwnerType.STORE,s.getStoreId());
        if(s.getNetAmountVnd()>0) {
            var credit=post(wallet.getWalletId(),WalletTransactionType.SELLER_SETTLEMENT,WalletTransactionDirection.CREDIT,s.getNetAmountVnd(),"SETTLEMENT",id,"settlement:"+id);
            s.setWalletTransactionId(credit.transactionId());
        }
        s.setWalletId(wallet.getWalletId()); s.setStatus(SettlementStatus.PAID); s.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
        long fee=Math.addExact(number(s.getBreakdown(),"platformFeeVnd"),number(s.getBreakdown(),"serviceFeeVnd"));
        if(fee>0) {
            Wallet platform=ensureWallet(WalletOwnerType.PLATFORM,"SCANMS");
            post(platform.getWalletId(),WalletTransactionType.PLATFORM_FEE,WalletTransactionDirection.CREDIT,fee,"SETTLEMENT",id,"settlement:"+id+":fees");
        }
        return settlementView(settlements.save(s));
    }
    @Transactional(readOnly=true) public PageView<SettlementView> settlementList(String storeId,int page,int size) {
        Page<SellerSettlement> result;
        if(storeId==null) { access.operatorOnly(); result=settlements.findAll(paging(page,size,"createdAt")); }
        else { access.owner(WalletOwnerType.STORE,storeId); result=settlements.findByStoreId(storeId,paging(page,size,"createdAt")); }
        return new PageView<>(result.stream().map(this::settlementView).toList(),result.getTotalElements(),page,size);
    }
    public FeeConfigResponse createFee(CreateFeeConfigRequest input) {
        if(!access.role("ADMIN") && !access.role("SYSTEM_ADMIN")) throw new AppException(ErrorCode.FORBIDDEN);
        lockedWallet(ensureWallet(WalletOwnerType.PLATFORM,"SCANMS").getWalletId());
        FeeConfig c=feeMapper.toEntity(input); calculator.validate(c);
        if(c.getStatus()==FeeConfigStatus.ACTIVE) c.setActivatedAt(Instant.now());
        return feeMapper.toResponse(fees.saveAndFlush(c));
    }
    public FeeConfigResponse updateFee(String id,CreateFeeConfigRequest input) {
        if(!access.role("ADMIN") && !access.role("SYSTEM_ADMIN")) throw new AppException(ErrorCode.FORBIDDEN);
        lockedWallet(ensureWallet(WalletOwnerType.PLATFORM,"SCANMS").getWalletId());
        FeeConfig c=fees.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(c.getStatus()!=FeeConfigStatus.DRAFT || c.getActivatedAt()!=null || input.status()!=FeeConfigStatus.DRAFT) throw new AppException(ErrorCode.CONFLICT,"Only unused draft fees can be edited; create a new version for an applied fee");
        c.setFeeType(input.feeType()); c.setCalculationType(input.calculationType()); c.setRatePercent(input.ratePercent());
        c.setFixedAmountVnd(input.fixedAmountVnd()); c.setMinFeeVnd(input.minFeeVnd()); c.setMaxFeeVnd(input.maxFeeVnd());
        c.setValidFrom(input.validFrom()); c.setValidTo(input.validTo()); calculator.validate(c); return feeMapper.toResponse(fees.saveAndFlush(c));
    }
    public List<FeeConfigResponse> feeList() { access.operatorOnly(); return fees.findAll().stream().map(feeMapper::toResponse).toList(); }
    public FeeConfigResponse feeStatus(String id,FeeConfigStatus status) {
        if(!access.role("ADMIN") && !access.role("SYSTEM_ADMIN")) throw new AppException(ErrorCode.FORBIDDEN);
        lockedWallet(ensureWallet(WalletOwnerType.PLATFORM,"SCANMS").getWalletId());
        FeeConfig c=fees.findById(id).orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));
        if(status==FeeConfigStatus.DRAFT && (c.getStatus()!=FeeConfigStatus.DRAFT || c.getActivatedAt()!=null))
            throw new AppException(ErrorCode.CONFLICT,"An activated or retired fee cannot return to draft; create a new version");
        c.setStatus(status); calculator.validate(c);
        if(status==FeeConfigStatus.ACTIVE && c.getActivatedAt()==null) c.setActivatedAt(Instant.now());
        return feeMapper.toResponse(fees.save(c));
    }
    public WalletTransactionResponse post(String walletId,WalletTransactionType type,WalletTransactionDirection direction,long amount,String refType,String ref,String key) {
        return ledger.create(new CreateWalletTransactionRequest(walletId,type,direction,amount,WalletTransactionStatus.SUCCESS,refType,ref,key,type.name(),null));
    }
    private Pageable paging(int page,int size,String sort) {
        if(page<0 || size<1 || size>100) throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid page/size");
        return PageRequest.of(page,size,Sort.by(Sort.Direction.DESC,sort));
    }
    public static long number(Map<String,Object> map,String key) {
        try { return new java.math.BigDecimal(Objects.toString(map.get(key))).longValueExact(); }
        catch(Exception ex) { throw new AppException(ErrorCode.CONFLICT,"Missing or invalid contract field: "+key); }
    }
    public static String json(Object value) { return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(value); }
    @SuppressWarnings("unchecked") private Map<String,Object> snapshotMap(Map<String,Object> map,String key) {
        if(!(map.get(key) instanceof Map<?,?> value)) throw new AppException(ErrorCode.CONFLICT,"Fee snapshot missing");
        return (Map<String,Object>)value;
    }
    public WalletView walletView(Wallet w) { return new WalletView(w.getWalletId(),w.getOwnerType(),w.getOwnerRefId(),w.getCurrency(),w.getAvailableBalanceVnd(),w.getHeldBalanceVnd(),w.getStatus()); }
    public BankView bankView(BankAccount b) { return new BankView(b.getBankAccountId(),b.getStoreId(),b.getCollaboratorId(),b.getBankCode(),b.getHolderName(),b.getMaskedNumber(),b.getVerificationStatus(),b.getActive(),b.getSourceKycReference(),b.getReplacesBankId()); }
    public WithdrawalView withdrawalView(WithdrawalRequest w) { return new WithdrawalView(w.getWithdrawalId(),w.getWalletId(),w.getBankAccountId(),w.getAmountVnd(),w.getFeeVnd(),w.getNetAmountVnd(),w.getStatus(),w.getProviderReference(),w.getFailureReason(),w.getRequestedAt(),w.getApprovedAt(),w.getScheduledFor(),w.getCompletedAt()); }
    public SettlementView settlementView(SellerSettlement s) { return new SettlementView(s.getSettlementId(),s.getSellerOrderId(),s.getStoreId(),s.getNetAmountVnd(),s.getStatus(),s.getBreakdown(),s.getWalletTransactionId(),s.getPaidAt()); }
}
