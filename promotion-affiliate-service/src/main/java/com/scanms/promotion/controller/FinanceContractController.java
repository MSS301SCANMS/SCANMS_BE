package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.CommissionPolicyService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Slf4j
@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor @Transactional
public class FinanceContractController {
    private final EntityManager em;
    private final CommissionRepository commissions;
    private final CollaboratorProfileRepository collaborators;
    private final CommissionFinalizationRepository finalizations;
    private final ReferralLinkRepository referralLinks;
    private final CommissionPolicyService policyService;
    private final com.scanms.promotion.service.ProductCommissionDealService productDeals;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;
    private final RestClient http=http();
    private static RestClient http() {
        var factory=new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15)); return RestClient.builder().requestFactory(factory).build();
    }
    private void trusted(Jwt jwt,boolean operator) {
        Set<String> roles=new HashSet<>(); Object realm=jwt.getClaim("realm_access");
        if(realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
        Object resources=jwt.getClaim("resource_access");
        if(resources instanceof Map<?,?> map) map.values().forEach(v -> { if(v instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString())); });
        if(!roles.contains("PAYMENT_INTERNAL") && !(operator && roles.stream().anyMatch(Set.of("ADMIN","MANAGER","SYSTEM_ADMIN","SYSTEM_MANAGER","ORDER_INTERNAL","AFFILIATE_INTERNAL")::contains))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private Commission find(String id) {
        Commission c=em.find(Commission.class,id,LockModeType.PESSIMISTIC_WRITE); if(c==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return c;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> order(String path,Jwt jwt) {
        Map<?,?> response=http.get().uri(orderUrl+path).headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
        if(response==null || !(response.get("result") instanceof Map<?,?>)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Order contract unavailable");
        return (Map<String,Object>)response.get("result");
    }
    @GetMapping("/commission-basis/{id}") public ApiResponse<Map<String,Object>> basis(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Commission c=find(id);
        if(!Set.of(CommissionStatus.ELIGIBLE,CommissionStatus.ADJUSTED,CommissionStatus.CREDITED,CommissionStatus.PAID).contains(c.getStatus()) || c.getCommissionAmountVnd()==null || c.getCommissionAmountVnd()<=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission is not eligible");
        CollaboratorProfile profile=collaborators.findById(c.getCollaboratorId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(!Set.of("ACTIVE","APPROVED").contains(profile.getApprovalStatus().name())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Collaborator is inactive");
        Map<String,Object> item=order("/api/v1/order-items/"+c.getOrderItemId(),jwt);
        // Reuse Order's eligibility check: delivered + 14 days and no unresolved returns.
        Map<String,Object> seller=order("/api/v1/finance/settlement-basis/"+item.get("sellerOrderId"),jwt);
        if(seller.get("returnDeadline")==null) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if(finalizations.findById(c.getOrderItemId()).map(f -> f.getFinalizedAt()==null).orElse(true)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission has not been finalized");
        return ApiResponse.success(Map.of("commissionId",id,"collaboratorId",c.getCollaboratorId(),"amountVnd",c.getCommissionAmountVnd(),"orderItemId",c.getOrderItemId(),"currency","VND"));
    }
    public record CreditInput(String transactionId,Long amountVnd) {}
    @PostMapping("/commissions/{id}/credited") public ApiResponse<Map<String,Object>> credited(@PathVariable String id,@RequestBody CreditInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,false); Commission c=find(id);
        if(input.transactionId()==null || input.amountVnd()==null || input.amountVnd()<=0 || input.amountVnd()<c.getCommissionAmountVnd()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission credit mismatch");
        if(c.getTransferReference()!=null && !c.getTransferReference().equals(input.transactionId())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Another credit already exists");
        if(!Set.of(CommissionStatus.ELIGIBLE,CommissionStatus.ADJUSTED,CommissionStatus.CREDITED,CommissionStatus.PAID,CommissionStatus.CANCELLED).contains(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        var history=new LinkedHashMap<>(c.getAdjustmentHistory()==null?Map.<String,Object>of():c.getAdjustmentHistory());
        // A delayed initial acknowledgement must not erase a newer reversal acknowledgement.
        history.putIfAbsent("ledgerReconciledAmountVnd",input.amountVnd());
        c.setAdjustmentHistory(history); c.setTransferReference(input.transactionId());
        Object reconciledAmount=history.get("ledgerReconciledAmountVnd");
        c.setStatus(c.getCommissionAmountVnd()==0?CommissionStatus.CANCELLED:reconciledAmount instanceof Number n && n.longValue()==c.getCommissionAmountVnd()?CommissionStatus.CREDITED:CommissionStatus.ADJUSTED);
        if(c.getPaidAt()==null) c.setPaidAt(LocalDateTime.now(ZoneOffset.UTC)); commissions.save(c);
        return ApiResponse.success(Map.of("commissionId",id,"status",c.getStatus().name()));
    }
    @GetMapping("/commission-reconciliation-basis/{id}") public ApiResponse<Map<String,Object>> reconciliationBasis(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Commission c=find(id);
        if(c.getTransferReference()==null || c.getCommissionAmountVnd()==null || c.getCommissionAmountVnd()<0)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission has not been credited");
        var basis=order("/api/v1/finance/commission-adjustment-item/"+c.getOrderItemId(),jwt);
        long retained=new BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact();
        long expected=BigDecimal.valueOf(retained).multiply(c.getRateSnapshot()).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact();
        if(!Objects.equals(c.getCommissionAmountVnd(),expected)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission adjustment is not up to date");
        return ApiResponse.success(Map.of("commissionId",id,"collaboratorId",c.getCollaboratorId(),"amountVnd",expected,"originalTransactionId",c.getTransferReference(),"currency","VND"));
    }
    @PostMapping("/commissions/{id}/reconciled") public ApiResponse<Map<String,Object>> reconciled(@PathVariable String id,@RequestBody CreditInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,false); Commission c=find(id);
        if(c.getTransferReference()==null || input.amountVnd()==null || input.amountVnd()<c.getCommissionAmountVnd() || input.transactionId()==null)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission reconciliation mismatch");
        var history=new LinkedHashMap<>(c.getAdjustmentHistory()==null?Map.<String,Object>of():c.getAdjustmentHistory());
        Object previous=history.get("ledgerReconciledAmountVnd");
        if(previous instanceof Number n && input.amountVnd()>n.longValue()) return ApiResponse.success(Map.of("commissionId",id,"amountVnd",n.longValue()));
        history.put("ledgerReconciledAmountVnd",input.amountVnd());
        if(!input.transactionId().equals(c.getTransferReference())) history.put("reversalTransactionId",input.transactionId());
        c.setAdjustmentHistory(history);
        if(Objects.equals(input.amountVnd(),c.getCommissionAmountVnd())) c.setStatus(input.amountVnd()==0?CommissionStatus.CANCELLED:CommissionStatus.CREDITED);
        commissions.save(c); return ApiResponse.success(Map.of("commissionId",id,"amountVnd",input.amountVnd()));
    }
    @GetMapping("/commissions-total") public ApiResponse<Map<String,Object>> total(@RequestParam String orderItemIds,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Set<String> ids=new HashSet<>(Arrays.asList(orderItemIds.split(","))); long total=0;
        if(ids.isEmpty() || ids.contains("")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long finalizedTotal=0;
        for(String itemId:ids) {
            var f=finalizations.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Commission calculation has not been finalized for every item"));
            if(f.getFinalizedAt()==null || f.getAmountVnd()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission is still being calculated");
            finalizedTotal=Math.addExact(finalizedTotal,f.getAmountVnd());
        }
        for(Commission c:commissions.findAll().stream().filter(c -> ids.contains(c.getOrderItemId())).toList()) {
            if(!Set.of(CommissionStatus.CANCELLED,CommissionStatus.ELIGIBLE,CommissionStatus.ADJUSTED,CommissionStatus.CREDITED,CommissionStatus.PAID).contains(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finalize commission adjustments first");
            if(c.getStatus()!=CommissionStatus.CANCELLED) {
                if(c.getCommissionAmountVnd()==null || c.getCommissionAmountVnd()<0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Invalid finalized commission amount");
                total=Math.addExact(total,c.getCommissionAmountVnd());
            }
        }
        if(total!=finalizedTotal) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finalized commission snapshot changed");
        return ApiResponse.success(Map.of("amountVnd",total,"finalized",true));
    }
    // ─── Commission Initialization ────────────────────────────────────────────────

    public record InitInput(String reason) {}

    /**
     * Endpoint khởi tạo Commission cho một OrderItem.
     * Gọi bởi order-service sau payment SUCCESS (hoặc admin thủ công).
     * Idempotent: gọi nhiều lần với cùng itemId đều trả kết quả nhất quán.
     */
    @PostMapping("/commission-initializations/{itemId}")
    public ApiResponse<Map<String,Object>> initializeCommission(
            @PathVariable String itemId,
            @RequestBody(required = false) InitInput input,
            @AuthenticationPrincipal Jwt jwt) {
        trusted(jwt, true);
        return ApiResponse.success(doInitialize(itemId, jwt));
    }

    /**
     * Tạo Commission row với rateSnapshot tại thời điểm gọi.
     * Flow: OrderItem.referralLinkId → ReferralLink → CollaboratorProfile (APPROVED?) → CommissionPolicy resolver → Commission(PENDING)
     */
    @SuppressWarnings("unchecked")
    private Map<String,Object> doInitialize(String itemId, Jwt jwt) {
        // Lock để tránh race condition
        CommissionFinalization anchor = em.find(CommissionFinalization.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        if (anchor != null && anchor.getFinalizedAt() != null) {
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "Item already finalized");
        }
        // Idempotent: Commission đã có thì trả về luôn
        List<Commission> existing = commissions.findByOrderItemId(itemId);
        if (!existing.isEmpty()) {
            Commission c = existing.get(0);
            return Map.of("orderItemId", itemId, "initialized", true,
                    "commissionId", c.getCommissionId(), "status", c.getStatus().name());
        }
        // Lấy OrderItem từ order-service
        Map<String,Object> itemDetails;
        try { itemDetails = order("/api/v1/order-items/" + itemId, jwt); }
        catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot fetch order item: " + ex.getMessage()); }

        String referralLinkId = Objects.toString(itemDetails.get("referralLinkId"), null);
        if (referralLinkId == null) {
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "No referral attribution");
        }
        // Tìm ReferralLink
        ReferralLink referralLink = referralLinks.findById(referralLinkId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "ReferralLink not found: " + referralLinkId));
        String collaboratorId = referralLink.getCollaboratorId();
        // Validate collaborator APPROVED
        CollaboratorProfile profile = collaborators.findById(collaboratorId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Collaborator profile not found: " + collaboratorId));
        if (!Set.of("ACTIVE", "APPROVED").contains(profile.getApprovalStatus().name())) {
            log.warn("Commission init skipped: collaborator {} status={}", collaboratorId, profile.getApprovalStatus());
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "Collaborator not approved: " + profile.getApprovalStatus());
        }
        // Lấy storeId (best-effort, dùng cho STORE-level policy resolution)
        String storeId = null;
        String sellerOrderId = Objects.toString(itemDetails.get("sellerOrderId"), null);
        if (sellerOrderId != null) {
            try {
                Map<?,?> resp = http.get().uri(orderUrl + "/api/v1/seller-orders/" + sellerOrderId)
                        .headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
                if (resp != null && resp.get("result") instanceof Map<?,?> r) storeId = Objects.toString(r.get("storeId"), null);
            } catch(Exception ex) { throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot verify seller store for commission; retry required"); }
        }
        if (storeId == null || storeId.isBlank()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Missing seller store for commission");
        if (itemDetails.get("productId") != null && !referralLink.getProductId().equals(itemDetails.get("productId")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order product does not match referral product");
        // Resolve CommissionPolicy: COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM
        CommissionPolicy policy = policyService.resolvePolicy(
                collaboratorId, referralLink.getLivestreamId(), referralLink.getProductId(), storeId);
        if (policy == null) {
            log.warn("No active policy for collaborator={} product={} store={}", collaboratorId, referralLink.getProductId(), storeId);
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "No applicable active commission policy");
        }
        Instant orderCreatedAt = null;
        if (itemDetails.get("placedAt") != null) {
            String date = itemDetails.get("placedAt").toString();
            try { orderCreatedAt = Instant.parse(date); }
            catch (java.time.format.DateTimeParseException ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid order placement instant for commission deal");
            }
        }
        var agreedRate = productDeals.rate(collaboratorId, referralLink.getProductId(), storeId, orderCreatedAt, policy);
        // Tạo CommissionFinalization anchor nếu chưa có
        if (anchor == null) { anchor = new CommissionFinalization(); anchor.setOrderItemId(itemId); em.persist(anchor); em.flush(); }
        // Tạo Commission với rate snapshot tại thời điểm này (không thay đổi dù policy sau đổi)
        Commission commission = new Commission();
        commission.setOrderItemId(itemId);
        commission.setCollaboratorId(collaboratorId);
        commission.setReferralLinkId(referralLinkId);
        commission.setCommissionPolicyId(agreedRate.policyId());
        commission.setRateSnapshot(agreedRate.totalRate());
        long initialBasis=new BigDecimal(itemDetails.get("netPaidAmountVnd").toString()).longValueExact();
        long initialAmount=BigDecimal.valueOf(initialBasis).multiply(agreedRate.totalRate()).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact();
        commission.setBasisAmountVnd(initialBasis);
        commission.setCommissionAmountVnd(initialAmount);
        commission.setStatus(CommissionStatus.PENDING);
        commission.setAdjustmentHistory(Map.of("initializedAt", Instant.now().toString(),
                "policyId", agreedRate.policyId(), "scope", policy.getScopeType().name(),
                "rateSnapshot", agreedRate.totalRate().toPlainString()));
        var initialHistory=new LinkedHashMap<>(commission.getAdjustmentHistory());
        initialHistory.put("baseRate", agreedRate.baseRate());
        initialHistory.put("extraPoints", agreedRate.extraPoints());
        if (agreedRate.dealId() != null) initialHistory.put("productDealId", agreedRate.dealId());
        if(itemDetails.get("productSnapshot") instanceof Map<?,?> product) initialHistory.put("productName",Objects.toString(product.get("name"),Objects.toString(product.get("title"),"Sản phẩm")));
        initialHistory.put("originalBasisVnd",initialBasis); initialHistory.put("originalAmountVnd",initialAmount);
        commission.setAdjustmentHistory(initialHistory);
        Commission saved = commissions.save(commission);
        log.info("Commission {} created: item={} collaborator={} policy={} rate={}",
                saved.getCommissionId(), itemId, collaboratorId, policy.getPolicyId(), policy.getCommissionRate());
        return Map.of("orderItemId", itemId, "initialized", true,
                "commissionId", saved.getCommissionId(), "rateSnapshot", agreedRate.totalRate().toPlainString());
    }

    // ─── Commission Adjustment (return / refund) ─────────────────────────────────

    public record AdjustInput(String reason) {}

    /**
     * Recalculate commission amount after return/refund using current retained basis.
     * PENDING rows stay PENDING (finalize sẽ tính lại). ELIGIBLE/CREDITED/PAID → ADJUSTED.
     */
    @PostMapping("/commission-adjustments/{itemId}")
    public ApiResponse<Map<String,Object>> adjustCommission(
            @PathVariable String itemId,
            @RequestBody(required = false) AdjustInput input,
            @AuthenticationPrincipal Jwt jwt) {
        trusted(jwt, true);
        CommissionFinalization anchor = em.find(CommissionFinalization.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        Map<String,Object> basis = order("/api/v1/finance/commission-adjustment-item/" + itemId, jwt);
        long retained = new BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact();
        String reason = input != null && input.reason() != null && !input.reason().isBlank()
                ? input.reason() : "Return/refund adjustment";
        int adjusted = 0;
        for (Commission candidate : commissions.findByOrderItemId(itemId)) {
            Commission c = find(candidate.getCommissionId());
            if (c.getStatus() == CommissionStatus.CANCELLED) continue;
            if (c.getRateSnapshot() == null) throw new ResponseStatusException(HttpStatus.CONFLICT, "Missing rate snapshot");
            long amount = BigDecimal.valueOf(retained)
                    .multiply(c.getRateSnapshot())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (Objects.equals(c.getCommissionAmountVnd(), amount) && Objects.equals(c.getBasisAmountVnd(), retained))
                continue;
            Map<String,Object> history = new LinkedHashMap<>(c.getAdjustmentHistory() == null ? Map.of() : c.getAdjustmentHistory());
            if(!history.containsKey("adjustedAt") && !history.containsKey("finalizedAt")) {
                history.putIfAbsent("originalAmountVnd", c.getCommissionAmountVnd());
                history.putIfAbsent("originalBasisVnd", c.getBasisAmountVnd());
            }
            history.put("adjustedAt", Instant.now().toString());
            history.put("reason", reason);
            history.put("beforeAmountVnd", c.getCommissionAmountVnd() == null ? 0 : c.getCommissionAmountVnd());
            history.put("afterAmountVnd", amount);
            history.put("beforeBasisVnd", c.getBasisAmountVnd() == null ? 0 : c.getBasisAmountVnd());
            history.put("afterBasisVnd", retained);
            history.put("refundedAmountVnd",basis.getOrDefault("refundedAmountVnd",0L));
            c.setAdjustmentHistory(history);
            c.setBasisAmountVnd(retained);
            c.setCommissionAmountVnd(amount);
            c.setStatus(amount == 0 ? CommissionStatus.CANCELLED : c.getStatus()==CommissionStatus.PENDING ? CommissionStatus.PENDING : CommissionStatus.ADJUSTED);
            commissions.save(c);
            adjusted++;
        }
        // Keep finalized snapshot consistent when already finalized
        if (anchor != null && anchor.getFinalizedAt() != null) {
            long total = commissions.findByOrderItemId(itemId).stream()
                    .filter(c -> c.getStatus() != CommissionStatus.CANCELLED && c.getCommissionAmountVnd() != null)
                    .mapToLong(Commission::getCommissionAmountVnd).sum();
            anchor.setAmountVnd(total);
            anchor.setReason(reason);
            finalizations.save(anchor);
        }
        return ApiResponse.success(Map.of("orderItemId", itemId, "adjusted", adjusted, "basisAmountVnd", retained));
    }

    // ─── Commission Finalization (14-day rule) ────────────────────────────────────

    public record FinalizeInput(String reason) {}
    @PostMapping("/commission-finalizations/{itemId}") public ApiResponse<Map<String,Object>> finalizeItem(@PathVariable String itemId,@RequestBody FinalizeInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true);
        if(input.reason()==null || input.reason().isBlank() || input.reason().length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Finalization reason required");
        CommissionFinalization anchor=em.find(CommissionFinalization.class,itemId,LockModeType.PESSIMISTIC_WRITE);
        if(anchor==null) { anchor=new CommissionFinalization(); anchor.setOrderItemId(itemId); em.persist(anchor); em.flush(); }
        Map<String,Object> basis=order("/api/v1/finance/commission-order-item/"+itemId,jwt);
        if(anchor.getFinalizedAt()!=null) return ApiResponse.success(Map.of("orderItemId",itemId,"finalized",true));
        long retained=new BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact(), total=0;
        // Dùng findByOrderItemId thay vì findAll().filter() để hiệu quả hơn
        List<Commission> rows=commissions.findByOrderItemId(itemId).stream().sorted(Comparator.comparing(Commission::getCommissionId)).toList();
        // Auto-init nếu thiếu Commission row — trường hợp commission-initializations chưa được gọi trước đó
        if(rows.isEmpty() && Boolean.TRUE.equals(basis.get("commissionApplicable"))) {
            log.info("Auto-initializing commission for orderItem={} during finalization", itemId);
            try { doInitialize(itemId, jwt); }
            catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission auto-init failed: " + ex.getMessage()); }
            rows=commissions.findByOrderItemId(itemId).stream().sorted(Comparator.comparing(Commission::getCommissionId)).toList();
            if(rows.isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Chưa có chính sách hoa hồng hợp lệ; chưa thể chốt đơn có KOL giới thiệu.");
        }
        for(Commission candidate:rows) {
            Commission c=find(candidate.getCommissionId());
            if(c.getRateSnapshot()==null || c.getRateSnapshot().signum()<0 || c.getRateSnapshot().compareTo(BigDecimal.valueOf(100))>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission policy snapshot is missing or invalid");
            long amount=c.getStatus()==CommissionStatus.CANCELLED?0:BigDecimal.valueOf(retained).multiply(c.getRateSnapshot()).divide(BigDecimal.valueOf(100),0,RoundingMode.HALF_UP).longValueExact();
            if(c.getStatus()==CommissionStatus.CREDITED || c.getStatus()==CommissionStatus.PAID) {
                if(!Objects.equals(c.getCommissionAmountVnd(),amount)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Already credited commission requires a ledger adjustment");
            } else {
                Map<String,Object> history=new LinkedHashMap<>(c.getAdjustmentHistory()==null?Map.of():c.getAdjustmentHistory());
                if(!history.containsKey("adjustedAt") && !history.containsKey("finalizedAt")) {
                    history.putIfAbsent("originalAmountVnd", c.getCommissionAmountVnd());
                    history.putIfAbsent("originalBasisVnd", c.getBasisAmountVnd());
                }
                history.put("refundedAmountVnd", basis.getOrDefault("refundedAmountVnd", 0L));
                history.put("afterAmountVnd", amount);
                history.put("beforeAmountVnd",c.getCommissionAmountVnd()==null?0:c.getCommissionAmountVnd()); history.put("finalBasisAmountVnd",retained); history.put("finalizedAt",Instant.now().toString());
                c.setAdjustmentHistory(history); c.setBasisAmountVnd(retained); c.setCommissionAmountVnd(amount);
                c.setStatus(amount==0?CommissionStatus.CANCELLED:CommissionStatus.ELIGIBLE); commissions.save(c);
            }
            if(c.getStatus()!=CommissionStatus.CANCELLED) total=Math.addExact(total,c.getCommissionAmountVnd());
        }
        if(total>retained) throw new ResponseStatusException(HttpStatus.CONFLICT,"Combined commission exceeds retained paid value");
        anchor.setAmountVnd(total); anchor.setFinalizedAt(Instant.now()); anchor.setReason(input.reason()); anchor.setFinalizedBy(jwt.getSubject()); finalizations.saveAndFlush(anchor);
        return ApiResponse.success(Map.of("orderItemId",itemId,"amountVnd",total,"finalized",true));
    }
}
