package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CommissionStatus;
import com.scanms.promotion.dto.request.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.CommissionPolicyService;
import com.scanms.promotion.service.FinanceContractService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class FinanceContractServiceImpl implements FinanceContractService {
    private final EntityManager em;
    private final CommissionRepository commissions;
    private final CollaboratorProfileRepository collaborators;
    private final CommissionFinalizationRepository finalizations;
    private final ReferralLinkRepository referralLinks;
    private final CommissionPolicyService policyService;
    @Value("${clients.order.url:http://localhost:8083}")
    private String orderUrl;
    private final RestClient http = http();

    private static RestClient http() {
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    private void trusted(Jwt jwt, boolean operator) {
        Set<String> roles = new HashSet<>();
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> list) {
            list.forEach(r -> roles.add(r.toString()));
        }
        Object resources = jwt.getClaim("resource_access");
        if (resources instanceof Map<?, ?> map) {
            map.values().forEach(v -> {
                if (v instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> list) {
                    list.forEach(r -> roles.add(r.toString()));
                }
            });
        }
        if (!roles.contains("PAYMENT_INTERNAL")
                && !(operator
                        && roles.stream()
                                .anyMatch(Set.of(
                                                "ADMIN",
                                                "MANAGER",
                                                "SYSTEM_ADMIN",
                                                "SYSTEM_MANAGER",
                                                "ORDER_INTERNAL",
                                                "AFFILIATE_INTERNAL")::contains))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    private Commission find(String id) {
        Commission c = em.find(Commission.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (c == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        return c;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> order(String path, Jwt jwt) {
        Map<?, ?> response = http.get()
                .uri(orderUrl + path)
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("result") instanceof Map<?, ?>)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Order contract unavailable");
        }
        return (Map<String, Object>) response.get("result");
    }

    @Override
    public Map<String, Object> basis(String id, Jwt jwt) {
        trusted(jwt, true);
        Commission c = find(id);
        if (!Set.of(
                        CommissionStatus.ELIGIBLE,
                        CommissionStatus.ADJUSTED,
                        CommissionStatus.CREDITED,
                        CommissionStatus.PAID)
                .contains(c.getStatus())
                || c.getCommissionAmountVnd() == null
                || c.getCommissionAmountVnd() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission is not eligible");
        }
        CollaboratorProfile profile = collaborators.findById(c.getCollaboratorId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!Set.of("ACTIVE", "APPROVED").contains(profile.getApprovalStatus().name())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Collaborator is inactive");
        }
        Map<String, Object> item = order("/api/v1/order-items/" + c.getOrderItemId(), jwt);
        Map<String, Object> seller = order("/api/v1/finance/settlement-basis/" + item.get("sellerOrderId"), jwt);
        if (seller.get("returnDeadline") == null) throw new ResponseStatusException(HttpStatus.CONFLICT);
        if (finalizations.findById(c.getOrderItemId()).map(f -> f.getFinalizedAt() == null).orElse(true)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission has not been finalized");
        }
        return Map.of(
                "commissionId", id,
                "collaboratorId", c.getCollaboratorId(),
                "amountVnd", c.getCommissionAmountVnd(),
                "orderItemId", c.getOrderItemId(),
                "currency", "VND");
    }

    @Override
    public Map<String, Object> credited(String id, CreditInput input, Jwt jwt) {
        trusted(jwt, false);
        Commission c = find(id);
        if (input.transactionId() == null || !Objects.equals(input.amountVnd(), c.getCommissionAmountVnd())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission credit mismatch");
        }
        if (c.getTransferReference() != null && !c.getTransferReference().equals(input.transactionId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Another credit already exists");
        }
        if (!Set.of(
                        CommissionStatus.ELIGIBLE,
                        CommissionStatus.ADJUSTED,
                        CommissionStatus.CREDITED,
                        CommissionStatus.PAID)
                .contains(c.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT);
        }
        c.setTransferReference(input.transactionId());
        c.setStatus(CommissionStatus.CREDITED);
        c.setPaidAt(LocalDateTime.now(ZoneOffset.UTC));
        commissions.save(c);
        return Map.of("commissionId", id, "status", "CREDITED");
    }

    @Override
    public Map<String, Object> total(String orderItemIds, Jwt jwt) {
        trusted(jwt, true);
        Set<String> ids = new HashSet<>(Arrays.asList(orderItemIds.split(",")));
        long total = 0;
        if (ids.isEmpty() || ids.contains("")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        long finalizedTotal = 0;
        for (String itemId : ids) {
            var f = finalizations.findById(itemId)
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.CONFLICT, "Commission calculation has not been finalized for every item"));
            if (f.getFinalizedAt() == null || f.getAmountVnd() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission is still being calculated");
            }
            finalizedTotal = Math.addExact(finalizedTotal, f.getAmountVnd());
        }
        for (Commission c :
                commissions.findAll().stream().filter(c -> ids.contains(c.getOrderItemId())).toList()) {
            if (!Set.of(
                            CommissionStatus.CANCELLED,
                            CommissionStatus.ELIGIBLE,
                            CommissionStatus.ADJUSTED,
                            CommissionStatus.CREDITED,
                            CommissionStatus.PAID)
                    .contains(c.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalize commission adjustments first");
            }
            if (c.getStatus() != CommissionStatus.CANCELLED) {
                if (c.getCommissionAmountVnd() == null || c.getCommissionAmountVnd() < 0) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid finalized commission amount");
                }
                total = Math.addExact(total, c.getCommissionAmountVnd());
            }
        }
        if (total != finalizedTotal) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Finalized commission snapshot changed");
        }
        return Map.of("amountVnd", total, "finalized", true);
    }

    @Override
    public Map<String, Object> initializeCommission(String itemId, InitInput input, Jwt jwt) {
        trusted(jwt, true);
        return doInitialize(itemId, jwt);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> doInitialize(String itemId, Jwt jwt) {
        CommissionFinalization anchor =
                em.find(CommissionFinalization.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        if (anchor != null && anchor.getFinalizedAt() != null) {
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "Item already finalized");
        }
        List<Commission> existing = commissions.findByOrderItemId(itemId);
        if (!existing.isEmpty()) {
            Commission c = existing.get(0);
            return Map.of(
                    "orderItemId", itemId,
                    "initialized", true,
                    "commissionId", c.getCommissionId(),
                    "status", c.getStatus().name());
        }
        Map<String, Object> itemDetails;
        try {
            itemDetails = order("/api/v1/order-items/" + itemId, jwt);
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cannot fetch order item: " + ex.getMessage());
        }

        String referralLinkId = Objects.toString(itemDetails.get("referralLinkId"), null);
        if (referralLinkId == null) {
            return Map.of("orderItemId", itemId, "initialized", false, "reason", "No referral attribution");
        }
        ReferralLink referralLink = referralLinks.findById(referralLinkId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "ReferralLink not found: " + referralLinkId));
        String collaboratorId = referralLink.getCollaboratorId();
        CollaboratorProfile profile = collaborators.findById(collaboratorId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.CONFLICT, "Collaborator profile not found: " + collaboratorId));
        if (!Set.of("ACTIVE", "APPROVED").contains(profile.getApprovalStatus().name())) {
            log.warn("Commission init skipped: collaborator {} status={}", collaboratorId, profile.getApprovalStatus());
            return Map.of(
                    "orderItemId", itemId,
                    "initialized", false,
                    "reason", "Collaborator not approved: " + profile.getApprovalStatus());
        }
        String storeId = null;
        String sellerOrderId = Objects.toString(itemDetails.get("sellerOrderId"), null);
        if (sellerOrderId != null) {
            try {
                Map<?, ?> resp = http.get()
                        .uri(orderUrl + "/api/v1/seller-orders/" + sellerOrderId)
                        .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                        .retrieve()
                        .body(Map.class);
                if (resp != null && resp.get("result") instanceof Map<?, ?> r) {
                    storeId = Objects.toString(r.get("storeId"), null);
                }
            } catch (Exception ex) {
                log.debug("Could not fetch storeId for seller order {}: {}", sellerOrderId, ex.getMessage());
            }
        }
        CommissionPolicy policy = policyService.resolvePolicy(
                collaboratorId, referralLink.getLivestreamId(), referralLink.getProductId(), storeId);
        if (policy == null) {
            log.warn(
                    "No active policy for collaborator={} product={} store={}",
                    collaboratorId,
                    referralLink.getProductId(),
                    storeId);
            return Map.of(
                    "orderItemId", itemId,
                    "initialized", false,
                    "reason", "No applicable active commission policy");
        }
        if (anchor == null) {
            anchor = new CommissionFinalization();
            anchor.setOrderItemId(itemId);
            em.persist(anchor);
            em.flush();
        }
        Commission commission = new Commission();
        commission.setOrderItemId(itemId);
        commission.setCollaboratorId(collaboratorId);
        commission.setReferralLinkId(referralLinkId);
        commission.setCommissionPolicyId(policy.getPolicyId());
        commission.setRateSnapshot(policy.getCommissionRate());
        commission.setBasisAmountVnd(0L);
        commission.setCommissionAmountVnd(0L);
        commission.setStatus(CommissionStatus.PENDING);
        commission.setAdjustmentHistory(Map.of(
                "initializedAt", Instant.now().toString(),
                "policyId", policy.getPolicyId(),
                "scope", policy.getScopeType().name(),
                "rateSnapshot", policy.getCommissionRate().toPlainString()));
        Commission saved = commissions.save(commission);
        log.info(
                "Commission {} created: item={} collaborator={} policy={} rate={}",
                saved.getCommissionId(),
                itemId,
                collaboratorId,
                policy.getPolicyId(),
                policy.getCommissionRate());
        return Map.of(
                "orderItemId", itemId,
                "initialized", true,
                "commissionId", saved.getCommissionId(),
                "rateSnapshot", policy.getCommissionRate().toPlainString());
    }

    @Override
    public Map<String, Object> adjustCommission(String itemId, AdjustInput input, Jwt jwt) {
        trusted(jwt, true);
        Map<String, Object> basis = order("/api/v1/finance/commission-order-item/" + itemId, jwt);
        long retained = new BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact();
        String reason = input != null && input.reason() != null && !input.reason().isBlank()
                ? input.reason()
                : "Return/refund adjustment";
        int adjusted = 0;
        for (Commission candidate : commissions.findByOrderItemId(itemId)) {
            Commission c = find(candidate.getCommissionId());
            if (c.getStatus() == CommissionStatus.PENDING || c.getStatus() == CommissionStatus.CANCELLED) continue;
            if (c.getRateSnapshot() == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Missing rate snapshot");
            }
            long amount = BigDecimal.valueOf(retained)
                    .multiply(c.getRateSnapshot())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            if (Objects.equals(c.getCommissionAmountVnd(), amount)
                    && Objects.equals(c.getBasisAmountVnd(), retained)) continue;
            Map<String, Object> history = new LinkedHashMap<>(
                    c.getAdjustmentHistory() == null ? Map.of() : c.getAdjustmentHistory());
            history.put("adjustedAt", Instant.now().toString());
            history.put("reason", reason);
            history.put("beforeAmountVnd", c.getCommissionAmountVnd() == null ? 0 : c.getCommissionAmountVnd());
            history.put("afterAmountVnd", amount);
            history.put("beforeBasisVnd", c.getBasisAmountVnd() == null ? 0 : c.getBasisAmountVnd());
            history.put("afterBasisVnd", retained);
            c.setAdjustmentHistory(history);
            c.setBasisAmountVnd(retained);
            c.setCommissionAmountVnd(amount);
            c.setStatus(amount == 0 ? CommissionStatus.CANCELLED : CommissionStatus.ADJUSTED);
            commissions.save(c);
            adjusted++;
        }
        CommissionFinalization anchor =
                em.find(CommissionFinalization.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        if (anchor != null && anchor.getFinalizedAt() != null) {
            long total = commissions.findByOrderItemId(itemId).stream()
                    .filter(c -> c.getStatus() != CommissionStatus.CANCELLED && c.getCommissionAmountVnd() != null)
                    .mapToLong(Commission::getCommissionAmountVnd)
                    .sum();
            anchor.setAmountVnd(total);
            anchor.setReason(reason);
            finalizations.save(anchor);
        }
        return Map.of("orderItemId", itemId, "adjusted", adjusted, "basisAmountVnd", retained);
    }

    @Override
    public Map<String, Object> finalizeItem(String itemId, FinalizeInput input, Jwt jwt) {
        trusted(jwt, true);
        if (input.reason() == null || input.reason().isBlank() || input.reason().length() > 500) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Finalization reason required");
        }
        CommissionFinalization anchor =
                em.find(CommissionFinalization.class, itemId, LockModeType.PESSIMISTIC_WRITE);
        if (anchor == null) {
            anchor = new CommissionFinalization();
            anchor.setOrderItemId(itemId);
            em.persist(anchor);
            em.flush();
        }
        Map<String, Object> basis = order("/api/v1/finance/commission-order-item/" + itemId, jwt);
        if (anchor.getFinalizedAt() != null) {
            return Map.of("orderItemId", itemId, "finalized", true);
        }
        long retained = new BigDecimal(basis.get("basisAmountVnd").toString()).longValueExact(), total = 0;
        List<Commission> rows = commissions.findByOrderItemId(itemId).stream()
                .sorted(Comparator.comparing(Commission::getCommissionId))
                .toList();
        if (rows.isEmpty() && Boolean.TRUE.equals(basis.get("commissionApplicable"))) {
            log.info("Auto-initializing commission for orderItem={} during finalization", itemId);
            try {
                doInitialize(itemId, jwt);
            } catch (Exception ex) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission auto-init failed: " + ex.getMessage());
            }
            rows = commissions.findByOrderItemId(itemId).stream()
                    .sorted(Comparator.comparing(Commission::getCommissionId))
                    .toList();
        }
        for (Commission candidate : rows) {
            Commission c = find(candidate.getCommissionId());
            if (c.getRateSnapshot() == null
                    || c.getRateSnapshot().signum() < 0
                    || c.getRateSnapshot().compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Commission policy snapshot is missing or invalid");
            }
            long amount = c.getStatus() == CommissionStatus.CANCELLED
                    ? 0
                    : BigDecimal.valueOf(retained)
                            .multiply(c.getRateSnapshot())
                            .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                            .longValueExact();
            if (c.getStatus() == CommissionStatus.CREDITED || c.getStatus() == CommissionStatus.PAID) {
                if (!Objects.equals(c.getCommissionAmountVnd(), amount)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Already credited commission requires a ledger adjustment");
                }
            } else {
                Map<String, Object> history = new LinkedHashMap<>(
                        c.getAdjustmentHistory() == null ? Map.of() : c.getAdjustmentHistory());
                history.put("beforeAmountVnd", c.getCommissionAmountVnd() == null ? 0 : c.getCommissionAmountVnd());
                history.put("finalBasisAmountVnd", retained);
                history.put("finalizedAt", Instant.now().toString());
                c.setAdjustmentHistory(history);
                c.setBasisAmountVnd(retained);
                c.setCommissionAmountVnd(amount);
                c.setStatus(amount == 0 ? CommissionStatus.CANCELLED : CommissionStatus.ELIGIBLE);
                commissions.save(c);
            }
            if (c.getStatus() != CommissionStatus.CANCELLED) total = Math.addExact(total, c.getCommissionAmountVnd());
        }
        if (total > retained) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Combined commission exceeds retained paid value");
        }
        anchor.setAmountVnd(total);
        anchor.setFinalizedAt(Instant.now());
        anchor.setReason(input.reason());
        anchor.setFinalizedBy(jwt.getSubject());
        finalizations.saveAndFlush(anchor);
        return Map.of("orderItemId", itemId, "amountVnd", total, "finalized", true);
    }
}
