package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.VoucherRedemptionRepository;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.SerializationFeature;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/checkout-vouchers")
@RequiredArgsConstructor
@Transactional
public class CheckoutVoucherController {
    private final EntityManager em;
    private final VoucherRedemptionRepository redemptions;

    @Value("${clients.order.url:http://localhost:8083}")
    private String orderUrl;

    public record Input(String code, String customerId, List<Map<String, Object>> lines) {}

    private void internal(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        Object realm = jwt.getClaim("realm_access");
        if (realm instanceof Map<?, ?> m && m.get("roles") instanceof Collection<?> list) {
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
        if (!roles.contains("ORDER_INTERNAL")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    @PostMapping("/{orderId}")
    public ApiResponse<Map<String, Object>> reserve(
            @PathVariable String orderId, @RequestBody Input input, @AuthenticationPrincipal Jwt jwt) {
        return reserve(orderId, input, jwt, false);
    }

    @PostMapping("/quote")
    public ApiResponse<Map<String, Object>> quote(@RequestBody Input input, @AuthenticationPrincipal Jwt jwt) {
        return reserve("quote", input, jwt, true);
    }

    private ApiResponse<Map<String, Object>> reserve(String orderId, Input input, Jwt jwt, boolean preview) {
        internal(jwt);
        if (input.customerId() == null || input.lines() == null || input.lines().isEmpty()
                || input.lines().size() > 100) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        }
        String hash = JsonMapper.builder()
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
                .build()
                .writeValueAsString(input);
        List<Voucher> found = em.createQuery("select v from Voucher v where upper(v.code)=:code", Voucher.class)
                .setParameter("code", input.code() == null ? "" : input.code().trim().toUpperCase(Locale.ROOT))
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();
        if (found.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher is unavailable");
        Voucher v = found.getFirst();
        CheckoutVoucher existing = preview ? null : em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (existing != null) {
            if (!hash.equals(existing.getRequestHash()) || "RELEASED".equals(existing.getStatus())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher reservation mismatch");
            }
            return ApiResponse.success(Map.of("allocations", existing.getAllocations()));
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (v.getValidFrom() == null || v.getValidUntil() == null || now.isBefore(v.getValidFrom())
                || !now.isBefore(v.getValidUntil()) || v.getValue() == null || v.getValue().signum() <= 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher is outside its validity period");
        }
        if (v.getLimits() != null) {
            for (String key : v.getLimits().keySet()) {
                if (!Set.of("totalUsageLimit", "perCustomerLimit").contains(key)) {
                    throw new ResponseStatusException(
                            HttpStatus.CONFLICT, "Voucher limit policy is unsupported; finalize its schema first");
                }
            }
            long uses = em.createQuery(
                            "select count(r) from CheckoutVoucher r where r.voucherId=:v and r.status<>'RELEASED'",
                            Long.class)
                    .setParameter("v", v.getVoucherId())
                    .getSingleResult();
            long mine = em.createQuery(
                            "select count(r) from CheckoutVoucher r where r.voucherId=:v and r.customerId=:c and r.status<>'RELEASED'",
                            Long.class)
                    .setParameter("v", v.getVoucherId())
                    .setParameter("c", input.customerId())
                    .getSingleResult();
            if (v.getLimits().get("totalUsageLimit") instanceof Number totalLimit && uses >= totalLimit.longValue()
                    || v.getLimits().get("perCustomerLimit") instanceof Number customerLimit
                            && mine >= customerLimit.longValue()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher usage limit reached");
            }
        }
        List<Map<String, Object>> eligible = input.lines().stream()
                .filter(l -> switch (v.getScopeType()) {
                    case PLATFORM -> true;
                    case STORE -> Objects.equals(v.getStoreId(), l.get("storeId"));
                    case PRODUCT -> Objects.equals(v.getProductId(), l.get("productId"));
                    case LIVESTREAM -> Objects.equals(v.getLivestreamId(), l.get("sourceLivestreamId"));
                })
                .toList();
        long subtotal = 0;
        for (var line : eligible) {
            subtotal = Math.addExact(subtotal, new BigDecimal(line.get("grossAmountVnd").toString()).longValueExact());
        }
        if (subtotal <= 0 || v.getMinimumSubtotal() != null && subtotal < v.getMinimumSubtotal()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher minimum or scope is not met");
        }
        if (v.getDiscountType() == VoucherDiscountType.PERCENTAGE
                && v.getValue().compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid voucher percentage");
        }
        long discount = v.getDiscountType() == VoucherDiscountType.PERCENTAGE
                ? BigDecimal.valueOf(subtotal)
                        .multiply(v.getValue())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                : v.getValue().longValueExact();
        if (v.getCap() != null) discount = Math.min(discount, v.getCap());
        discount = Math.min(subtotal, discount);
        String funded = v.getIssuerType() == VoucherIssuerType.PLATFORM ? "PLATFORM" : "STORE";
        BigDecimal platformRate = funded.equals("PLATFORM") ? BigDecimal.valueOf(100) : BigDecimal.ZERO;
        if (v.getFunding() != null && !v.getFunding().isEmpty()) {
            funded = Objects.toString(v.getFunding().get("fundedBy"), funded);
            if (!Set.of("PLATFORM", "STORE", "SHARED").contains(funded)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid funding policy");
            }
            platformRate = funded.equals("PLATFORM")
                    ? BigDecimal.valueOf(100)
                    : funded.equals("STORE")
                            ? BigDecimal.ZERO
                            : new BigDecimal(Objects.toString(v.getFunding().get("platformRatePercent"), "-1"));
            if (platformRate.signum() < 0 || platformRate.compareTo(BigDecimal.valueOf(100)) > 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Shared funding snapshot is incomplete");
            }
        }
        List<Map<String, Object>> allocations = new ArrayList<>();
        long allocated = 0, cumulativeGross = 0;
        for (int i = 0; i < eligible.size(); i++) {
            var line = eligible.get(i);
            long gross = new BigDecimal(line.get("grossAmountVnd").toString()).longValueExact();
            cumulativeGross = Math.addExact(cumulativeGross, gross);
            long next = BigInteger.valueOf(discount)
                    .multiply(BigInteger.valueOf(cumulativeGross))
                    .divide(BigInteger.valueOf(subtotal))
                    .longValueExact();
            long amount = next - allocated;
            allocated = next;
            long platform = BigDecimal.valueOf(amount)
                    .multiply(platformRate)
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                    .longValueExact();
            allocations.add(Map.of(
                    "variantId", line.get("variantId"),
                    "voucherId", v.getVoucherId(),
                    "allocatedAmountVnd", amount,
                    "fundedBy", funded,
                    "platformFundedVnd", platform,
                    "voucherVersion", v.getVersion()));
        }
        if (!preview) {
            CheckoutVoucher r = new CheckoutVoucher();
            r.setOrderId(orderId);
            r.setCustomerId(input.customerId());
            r.setVoucherId(v.getVoucherId());
            r.setRequestHash(hash);
            r.setStatus("RESERVED");
            r.setAllocations(allocations);
            em.persist(r);
            redemptions.save(VoucherRedemption.builder()
                    .orderId(orderId)
                    .voucherId(v.getVoucherId())
                    .userId(input.customerId())
                    .discountAmountVnd(discount)
                    .status(VoucherRedemptionStatus.RESERVED)
                    .build());
        }
        return ApiResponse.success(Map.of("allocations", allocations, "discountAmountVnd", discount));
    }

    @PostMapping("/{orderId}/release")
    public ApiResponse<Map<String, Object>> release(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null || "RELEASED".equals(r.getStatus())) return ApiResponse.success(Map.of("released", true));
        if (!"RESERVED".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A used voucher must be restored after cancellation");
        }
        var redemption = redemptions.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Redemption not found"));
        if (redemption.getStatus() != VoucherRedemptionStatus.RESERVED
                || !r.getVoucherId().equals(redemption.getVoucherId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher reservation is inconsistent");
        }
        r.setStatus("RELEASED");
        redemption.setStatus(VoucherRedemptionStatus.CANCELLED);
        redemption.setCancelledAt(Instant.now());
        return ApiResponse.success(Map.of("released", true));
    }

    @PostMapping("/{orderId}/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher reservation not found");
        var redemption = redemptions.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Redemption not found"));
        if (!r.getVoucherId().equals(redemption.getVoucherId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher reservation is inconsistent");
        }
        if ("COMMITTED".equals(r.getStatus()) && redemption.getStatus() == VoucherRedemptionStatus.USED) {
            return ApiResponse.success(Map.of("committed", true));
        }
        if (!"RESERVED".equals(r.getStatus()) || redemption.getStatus() != VoucherRedemptionStatus.RESERVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a reserved voucher can be committed");
        }
        r.setStatus("COMMITTED");
        redemption.setStatus(VoucherRedemptionStatus.USED);
        if (redemption.getRedeemedAt() == null) redemption.setRedeemedAt(Instant.now());
        return ApiResponse.success(Map.of("committed", true));
    }

    @PostMapping("/{orderId}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher reservation not found");
        if ("RELEASED".equals(r.getStatus())
                && redemptions.findByOrderId(orderId)
                        .map(x -> x.getStatus() == VoucherRedemptionStatus.RESTORED)
                        .orElse(false)) {
            return ApiResponse.success(Map.of("restored", true));
        }
        if (!"COMMITTED".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a used voucher can be restored");
        }
        var factory = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(10));
        Map<?, ?> response = RestClient.builder()
                .requestFactory(factory)
                .build()
                .get()
                .uri(orderUrl + "/api/v1/orders/" + orderId + "/voucher-restoration-eligibility")
                .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                .retrieve()
                .body(Map.class);
        if (response == null || !(response.get("result") instanceof Map<?, ?> result)
                || !Boolean.TRUE.equals(result.get("eligible"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher cannot be restored after delivery");
        }
        r.setStatus("RELEASED");
        var redemption = redemptions.findByOrderId(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Redemption not found"));
        redemption.setStatus(VoucherRedemptionStatus.RESTORED);
        redemption.setCancelledAt(Instant.now());
        return ApiResponse.success(Map.of("restored", true));
    }
}
