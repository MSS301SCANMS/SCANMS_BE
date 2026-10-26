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

    public record Input(String code, String customerId, List<Map<String, Object>> lines,
                        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) String shippingCode,
                        @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL) Map<String,Long> shippingFees) {
        public Input(String code,String customerId,List<Map<String,Object>> lines,String shippingCode) { this(code,customerId,lines,shippingCode,null); }
        public Input(String code, String customerId, List<Map<String,Object>> lines) { this(code,customerId,lines,null); }
    }

    public ApiResponse<Map<String,Object>> quote(Input input, Jwt jwt) {
        return reserve("quote",input,jwt,true,null);
    }

    public ApiResponse<Map<String,Object>> reserve(String orderId, Input input, Jwt jwt) {
        return reserve(orderId,input,jwt,false,null);
    }

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
            @PathVariable String orderId, @RequestBody Input input, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value="X-Shipping-Fee-Per-Store-Vnd",required=false) Long shippingFee) {
        return reserve(orderId, input, jwt, false, shippingFee);
    }

    @PostMapping("/quote")
    public ApiResponse<Map<String, Object>> quote(@RequestBody Input input, @AuthenticationPrincipal Jwt jwt,
            @RequestHeader(value="X-Shipping-Fee-Per-Store-Vnd",required=false) Long shippingFee) {
        return reserve("quote", input, jwt, true, shippingFee);
    }

    private ApiResponse<Map<String, Object>> reserve(String orderId, Input input, Jwt jwt, boolean preview, Long shippingFee) {
        internal(jwt);
        if (input.shippingCode()!=null && !input.shippingCode().isBlank())
            return reservePair(orderId,input,jwt,preview,shippingFee);
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
        String benefit = Objects.toString(v.getScope()==null?null:v.getScope().get("benefitType"),"GOODS");
        if (!Set.of("GOODS","SHIPPING").contains(benefit))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Unsupported voucher benefit");
        boolean shipping = "SHIPPING".equals(benefit);
        if (shipping) {
            if (v.getIssuerType()!=VoucherIssuerType.PLATFORM || (input.shippingFees()==null && (shippingFee==null || shippingFee<=0))
                    || !Set.of(VoucherScopeType.PLATFORM,VoucherScopeType.STORE).contains(v.getScopeType()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Voucher vận chuyển chưa áp dụng được cho đơn này");
            hash += ":shipping:" + shippingFee;
        }
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
                            "select count(r) from VoucherRedemption r where r.voucherId=:v and r.status in :states",
                            Long.class)
                    .setParameter("v", v.getVoucherId())
                    .setParameter("states",List.of(VoucherRedemptionStatus.RESERVED,VoucherRedemptionStatus.USED))
                    .getSingleResult();
            long mine = em.createQuery(
                            "select count(r) from VoucherRedemption r where r.voucherId=:v and r.userId=:c and r.status in :states",
                            Long.class)
                    .setParameter("v", v.getVoucherId())
                    .setParameter("states",List.of(VoucherRedemptionStatus.RESERVED,VoucherRedemptionStatus.USED))
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
        List<String> shippingStores = eligible.stream().map(line->Objects.toString(line.get("storeId"),"")).distinct().sorted().toList();
        if (shipping && shippingStores.stream().anyMatch(String::isBlank))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipping store is missing");
        var fees=new LinkedHashMap<String,Long>();
        if(shipping) for(String store:shippingStores) {
            Long fee=input.shippingFees()==null?shippingFee:input.shippingFees().get(store);
            if(fee==null || fee<0 || fee>10000000)throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipping fee snapshot is missing or invalid");
            fees.put(store,fee);
        }
        long basis = shipping ? fees.values().stream().reduce(0L,Math::addExact) : subtotal;
        if(shipping && basis<=0)throw new ResponseStatusException(HttpStatus.CONFLICT,"Không có phí vận chuyển để giảm");
        long discount = v.getDiscountType() == VoucherDiscountType.PERCENTAGE
                ? BigDecimal.valueOf(basis)
                        .multiply(v.getValue())
                        .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP)
                        .longValueExact()
                : v.getValue().longValueExact();
        if (v.getCap() != null) discount = Math.min(discount, v.getCap());
        discount = Math.min(basis, discount);
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
        if (shipping && !"PLATFORM".equals(funded))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Shipping voucher must be platform funded");
        List<Map<String, Object>> allocations = new ArrayList<>();
        long allocated = 0, cumulativeGross = 0;
        if (shipping) {
            long cumulativeFees=0;
            for (int i=0;i<shippingStores.size();i++) {
                cumulativeFees=Math.addExact(cumulativeFees,fees.get(shippingStores.get(i)));
                long next = BigInteger.valueOf(discount).multiply(BigInteger.valueOf(cumulativeFees))
                        .divide(BigInteger.valueOf(basis)).longValueExact();
                allocations.add(Map.of("benefitType","SHIPPING","storeId",shippingStores.get(i),
                        "voucherId",v.getVoucherId(),"allocatedAmountVnd",next-allocated,
                        "fundedBy","PLATFORM","voucherVersion",v.getVersion()));
                allocated = next;
            }
        } else for (int i = 0; i < eligible.size(); i++) {
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

    @SuppressWarnings("unchecked")
    private ApiResponse<Map<String,Object>> reservePair(String orderId, Input input, Jwt jwt, boolean preview, Long shippingFee) {
        if(input.customerId()==null || input.lines()==null || input.lines().isEmpty() || input.lines().size()>100)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        String goods=Objects.toString(input.code(),"").trim().toUpperCase(Locale.ROOT);
        String shipping=input.shippingCode().trim().toUpperCase(Locale.ROOT);
        if(goods.equals(shipping)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Choose different goods and shipping vouchers");
        List<String> codes=new ArrayList<>(List.of(shipping));
        if(!goods.isBlank()) codes.add(goods);
        // A stable locking order avoids deadlocks when checkouts use the same pair.
        List<Voucher> locked=em.createQuery("select v from Voucher v where upper(v.code) in :codes order by v.code",Voucher.class)
                .setParameter("codes",codes).setLockMode(LockModeType.PESSIMISTIC_WRITE).getResultList();
        if(locked.size()!=codes.size()) throw new ResponseStatusException(HttpStatus.CONFLICT,"Voucher is unavailable");
        for(Voucher voucher:locked) {
            String expected=shipping.equalsIgnoreCase(voucher.getCode())?"SHIPPING":"GOODS";
            String actual=Objects.toString(voucher.getScope()==null?null:voucher.getScope().get("benefitType"),"GOODS");
            if(!expected.equals(actual)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Only one goods voucher and one shipping voucher are allowed");
        }
        String hash=JsonMapper.builder().configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS,true).build().writeValueAsString(input)+":shipping:"+shippingFee;
        CheckoutVoucher existing=preview?null:em.find(CheckoutVoucher.class,orderId,LockModeType.PESSIMISTIC_WRITE);
        if(existing!=null) {
            if(!hash.equals(existing.getRequestHash()) || "RELEASED".equals(existing.getStatus()))
                throw new ResponseStatusException(HttpStatus.CONFLICT,"Voucher reservation mismatch");
            return ApiResponse.success(Map.of("allocations",existing.getAllocations()));
        }
        List<Map<String,Object>> allocations=new ArrayList<>();
        Map<String,Long> discounts=new LinkedHashMap<>();
        for(String code:codes) {
            var quote=reserve(orderId,new Input(code,input.customerId(),input.lines(),null,input.shippingFees()),jwt,true,shippingFee).result();
            allocations.addAll((List<Map<String,Object>>)quote.get("allocations"));
            String voucherId=locked.stream().filter(v->v.getCode().equalsIgnoreCase(code)).findFirst().orElseThrow().getVoucherId();
            discounts.put(voucherId,((Number)quote.get("discountAmountVnd")).longValue());
        }
        if(!preview) {
            CheckoutVoucher reservation=new CheckoutVoucher();
            reservation.setOrderId(orderId); reservation.setCustomerId(input.customerId());
            reservation.setVoucherId(discounts.keySet().iterator().next()); reservation.setRequestHash(hash);
            reservation.setStatus("RESERVED"); reservation.setAllocations(allocations); em.persist(reservation);
            discounts.forEach((id,amount)->redemptions.save(VoucherRedemption.builder().orderId(orderId).voucherId(id)
                    .userId(input.customerId()).discountAmountVnd(amount).status(VoucherRedemptionStatus.RESERVED).build()));
        }
        long total=0; for(long discount:discounts.values()) total=Math.addExact(total,discount);
        return ApiResponse.success(Map.of("allocations",allocations,"discountAmountVnd",total));
    }

    private List<VoucherRedemption> reservationRedemptions(CheckoutVoucher reservation) {
        var rows=redemptions.findAllByOrderId(reservation.getOrderId());
        Set<String> expected=new HashSet<>(List.of(reservation.getVoucherId()));
        for(var allocation:reservation.getAllocations()) expected.add(allocation.get("voucherId").toString());
        if(rows.size()!=expected.size() || !expected.equals(rows.stream().map(VoucherRedemption::getVoucherId).collect(java.util.stream.Collectors.toSet())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Voucher reservation is inconsistent");
        return rows;
    }

    @PostMapping("/{orderId}/release")
    public ApiResponse<Map<String, Object>> release(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null || "RELEASED".equals(r.getStatus())) return ApiResponse.success(Map.of("released", true));
        if (!"RESERVED".equals(r.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A used voucher must be restored after cancellation");
        }
        var rows = reservationRedemptions(r);
        if (rows.stream().anyMatch(redemption -> redemption.getStatus() != VoucherRedemptionStatus.RESERVED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Voucher reservation is inconsistent");
        }
        r.setStatus("RELEASED");
        rows.forEach(redemption -> { redemption.setStatus(VoucherRedemptionStatus.CANCELLED); redemption.setCancelledAt(Instant.now()); });
        return ApiResponse.success(Map.of("released", true));
    }

    @PostMapping("/{orderId}/commit")
    public ApiResponse<Map<String, Object>> commit(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher reservation not found");
        var rows = reservationRedemptions(r);
        if ("COMMITTED".equals(r.getStatus()) && rows.stream().allMatch(redemption -> redemption.getStatus() == VoucherRedemptionStatus.USED)) {
            return ApiResponse.success(Map.of("committed", true));
        }
        if (!"RESERVED".equals(r.getStatus()) || rows.stream().anyMatch(redemption -> redemption.getStatus() != VoucherRedemptionStatus.RESERVED)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only a reserved voucher can be committed");
        }
        r.setStatus("COMMITTED");
        rows.forEach(redemption -> { redemption.setStatus(VoucherRedemptionStatus.USED); if(redemption.getRedeemedAt()==null) redemption.setRedeemedAt(Instant.now()); });
        return ApiResponse.success(Map.of("committed", true));
    }

    @PostMapping("/{orderId}/restore")
    public ApiResponse<Map<String, Object>> restore(@PathVariable String orderId, @AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        CheckoutVoucher r = em.find(CheckoutVoucher.class, orderId, LockModeType.PESSIMISTIC_WRITE);
        if (r == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Voucher reservation not found");
        var rows = reservationRedemptions(r);
        if ("RELEASED".equals(r.getStatus()) && rows.stream().allMatch(x -> Set.of(VoucherRedemptionStatus.RESTORED,VoucherRedemptionStatus.CANCELLED).contains(x.getStatus()))) {
            return ApiResponse.success(Map.of("restored", rows.stream().anyMatch(x->x.getStatus()==VoucherRedemptionStatus.RESTORED)));
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
        if(rows.stream().anyMatch(x->x.getStatus()!=VoucherRedemptionStatus.USED)) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only used vouchers can be restored");
        var now=LocalDateTime.now(ZoneOffset.UTC);
        rows.forEach(redemption -> {
            var voucher=em.find(Voucher.class,redemption.getVoucherId());
            boolean valid=voucher!=null&&voucher.getValidFrom()!=null&&voucher.getValidUntil()!=null
                    &&!now.isBefore(voucher.getValidFrom())&&now.isBefore(voucher.getValidUntil());
            redemption.setStatus(valid?VoucherRedemptionStatus.RESTORED:VoucherRedemptionStatus.CANCELLED);
            redemption.setCancelledAt(Instant.now());
        });
        return ApiResponse.success(Map.of("restored",rows.stream().anyMatch(x->x.getStatus()==VoucherRedemptionStatus.RESTORED)));
    }

    @PostMapping("/{orderId}/cancel")
    public ApiResponse<Map<String,Object>> cancel(@PathVariable String orderId,@AuthenticationPrincipal Jwt jwt) {
        internal(jwt);
        var reservation=em.find(CheckoutVoucher.class,orderId,LockModeType.PESSIMISTIC_WRITE);
        if(reservation==null||"RELEASED".equals(reservation.getStatus()))return ApiResponse.success(Map.of("cancelled",true));
        if("RESERVED".equals(reservation.getStatus()))return release(orderId,jwt);
        return restore(orderId,jwt);
    }
}
