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

@Slf4j
@RestController @RequestMapping("/api/v1/finance") @RequiredArgsConstructor @Transactional
public class FinanceContractController {
    private final EntityManager em;
    private final CommissionRepository commissions;
    private final CollaboratorProfileRepository collaborators;
    private final CommissionFinalizationRepository finalizations;
    private final ReferralLinkRepository referralLinks;
    private final CommissionPolicyService policyService;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;
    private final RestClient http=http();
    private static RestClient http() {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15)); return RestClient.builder().requestFactory(factory).build();
    }
    private void trusted(Jwt jwt,boolean operator) {
        Set<String> roles=new HashSet<>(); Object realm=jwt.getClaim("realm_access");
        if(realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
        Object resources=jwt.getClaim("resource_access");
        if(resources instanceof Map<?,?> map) map.values().forEach(v -> { if(v instanceof Map<?,?> m && m.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString())); });
        if(!roles.contains("PAYMENT_INTERNAL") && !(operator && roles.stream().anyMatch(Set.of("ADMIN","MANAGER","SYSTEM_ADMIN","SYSTEM_MANAGER")::contains))) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private Commission find(String id) {
        Commission c=em.find(Commission.class,id,LockModeType.PESSIMISTIC_WRITE); if(c==null) throw new ResponseStatusException(HttpStatus.NOT_FOUND); return c;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> order(String path,Jwt jwt) {
        Map<?,?> response=http.get().uri(orderUrl+path).headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
        if(response==null || !(response.get("result") instanceof Map<?,?>)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Order contract unavailable");
        return (Map<String,Object>)response.get("result");
    }
    @GetMapping("/commission-basis/{id}") ApiResponse<Map<String,Object>> basis(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Commission c=find(id);
        if(!Set.of(CommissionStatus.ELIGIBLE,CommissionStatus.CREDITED,CommissionStatus.PAID).contains(c.getStatus()) || c.getCommissionAmountVnd()==null || c.getCommissionAmountVnd()<=0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission is not eligible");
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
    @PostMapping("/commissions/{id}/credited") ApiResponse<Map<String,Object>> credited(@PathVariable String id,@RequestBody CreditInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,false); Commission c=find(id);
        if(input.transactionId()==null || !Objects.equals(input.amountVnd(),c.getCommissionAmountVnd())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission credit mismatch");
        if(c.getTransferReference()!=null && !c.getTransferReference().equals(input.transactionId())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Another credit already exists");
        if(!Set.of(CommissionStatus.ELIGIBLE,CommissionStatus.CREDITED,CommissionStatus.PAID).contains(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT);
        c.setTransferReference(input.transactionId()); c.setStatus(CommissionStatus.CREDITED); c.setPaidAt(LocalDateTime.now(ZoneOffset.UTC)); commissions.save(c);
        return ApiResponse.success(Map.of("commissionId",id,"status","CREDITED"));
    }
    @GetMapping("/commissions-total") ApiResponse<Map<String,Object>> total(@RequestParam String orderItemIds,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true); Set<String> ids=new HashSet<>(Arrays.asList(orderItemIds.split(","))); long total=0;
        if(ids.isEmpty() || ids.contains("")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long finalizedTotal=0;
        for(String itemId:ids) {
            var f=finalizations.findById(itemId).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,"Commission calculation has not been finalized for every item"));
            if(f.getFinalizedAt()==null || f.getAmountVnd()==null) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission is still being calculated");
            finalizedTotal=Math.addExact(finalizedTotal,f.getAmountVnd());
        }
        for(Commission c:commissions.findAll().stream().filter(c -> ids.contains(c.getOrderItemId())).toList()) {
            if(!Set.of(CommissionStatus.CANCELLED,CommissionStatus.ELIGIBLE,CommissionStatus.CREDITED,CommissionStatus.PAID).contains(c.getStatus())) throw new ResponseStatusException(HttpStatus.CONFLICT,"Finalize commission adjustments first");
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
    ApiResponse<Map<String,Object>> initializeCommission(
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
            } catch(Exception ex) { log.debug("Could not fetch storeId for seller order {}: {}", sellerOrderId, ex.getMessage()); }
        }
        // Resolve CommissionPolicy: COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM
        CommissionPolicy policy = policyService.resolvePolicy(
                collaboratorId, referralLink.getLivestreamId(), referralLink.getProductId(), storeId);
        if (policy == null) {
            log.warn("No active policy for collaborator={} product={} store={}", collaboratorId, referralLink.getProductId(), storeId);
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "No applicable active commission policy");
        }
        // Tạo CommissionFinalization anchor nếu chưa có
        if (anchor == null) { anchor = new CommissionFinalization(); anchor.setOrderItemId(itemId); em.persist(anchor); em.flush(); }
        // Tạo Commission với rate snapshot tại thời điểm này (không thay đổi dù policy sau đổi)
        Commission commission = new Commission();
        commission.setOrderItemId(itemId);
        commission.setCollaboratorId(collaboratorId);
        commission.setReferralLinkId(referralLinkId);
        commission.setCommissionPolicyId(policy.getPolicyId());
        commission.setRateSnapshot(policy.getCommissionRate());
        commission.setBasisAmountVnd(0L);
        commission.setCommissionAmountVnd(0L);
        commission.setStatus(CommissionStatus.PENDING);
        commission.setAdjustmentHistory(Map.of("initializedAt", Instant.now().toString(),
                "policyId", policy.getPolicyId(), "scope", policy.getScopeType().name(),
                "rateSnapshot", policy.getCommissionRate().toPlainString()));
        Commission saved = commissions.save(commission);
        log.info("Commission {} created: item={} collaborator={} policy={} rate={}",
                saved.getCommissionId(), itemId, collaboratorId, policy.getPolicyId(), policy.getCommissionRate());
        return Map.of("orderItemId", itemId, "initialized", true,
                "commissionId", saved.getCommissionId(), "rateSnapshot", policy.getCommissionRate().toPlainString());
    }

    // ─── Commission Finalization (14-day rule) ────────────────────────────────────

    public record FinalizeInput(String reason) {}
    @PostMapping("/commission-finalizations/{itemId}") ApiResponse<Map<String,Object>> finalizeItem(@PathVariable String itemId,@RequestBody FinalizeInput input,@AuthenticationPrincipal Jwt jwt) {
        trusted(jwt,true);
        if(input.reason()==null || input.reason().isBlank() || input.reason().length()>500) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Finalization reason required");
        CommissionFinalization anchor=em.find(CommissionFinalization.class,itemId,LockModeType.PESSIMISTIC_WRITE);
        if(anchor==null) { anchor=new CommissionFinalization(); anchor.setOrderItemId(itemId); em.persist(anchor); em.flush(); }
        Map<String,Object> basis=order("/api/v1/finance/commission-order-item/"+itemId,jwt);
        if(anchor.getFinalizedAt()!=null) return ApiResponse.success(Map.of("orderItemId",itemId,"finalized",true));
        long retained=new java.math.BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact(), total=0;
        // Dùng findByOrderItemId thay vì findAll().filter() để hiệu quả hơn
        List<Commission> rows=commissions.findByOrderItemId(itemId).stream().sorted(Comparator.comparing(Commission::getCommissionId)).toList();
        // Auto-init nếu thiếu Commission row — trường hợp commission-initializations chưa được gọi trước đó
        if(rows.isEmpty() && Boolean.TRUE.equals(basis.get("commissionApplicable"))) {
            log.info("Auto-initializing commission for orderItem={} during finalization", itemId);
            try { doInitialize(itemId, jwt); }
            catch(Exception ex) { throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission auto-init failed: " + ex.getMessage()); }
            rows=commissions.findByOrderItemId(itemId).stream().sorted(Comparator.comparing(Commission::getCommissionId)).toList();
        }
        for(Commission candidate:rows) {
            Commission c=find(candidate.getCommissionId());
            if(c.getRateSnapshot()==null || c.getRateSnapshot().signum()<0 || c.getRateSnapshot().compareTo(java.math.BigDecimal.valueOf(100))>0) throw new ResponseStatusException(HttpStatus.CONFLICT,"Commission policy snapshot is missing or invalid");
            long amount=c.getStatus()==CommissionStatus.CANCELLED?0:java.math.BigDecimal.valueOf(retained).multiply(c.getRateSnapshot()).divide(java.math.BigDecimal.valueOf(100),0,java.math.RoundingMode.HALF_UP).longValueExact();
            if(c.getStatus()==CommissionStatus.CREDITED || c.getStatus()==CommissionStatus.PAID) {
                if(!Objects.equals(c.getCommissionAmountVnd(),amount)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Already credited commission requires a ledger adjustment");
            } else {
                Map<String,Object> history=new LinkedHashMap<>(c.getAdjustmentHistory()==null?Map.of():c.getAdjustmentHistory());
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
