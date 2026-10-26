package com.scanms.order.service.impl;

import com.scanms.order.constant.DiscountFundingType;
import com.scanms.order.constant.DiscountSourceType;
import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.entity.DiscountAllocation;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.repository.DiscountAllocationRepository;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.service.CheckoutService;
import com.scanms.order.service.CheckoutShippingPolicy;
import com.scanms.order.service.FinanceDispatch;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;
import tools.jackson.databind.json.JsonMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class CheckoutServiceImpl implements CheckoutService {
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private final DiscountAllocationRepository allocations;
    private final PlatformTransactionManager transactions;
    private final CheckoutShippingPolicy shippingPolicy;
    @Value("${clients.product.url:http://localhost:8082}")
    private String productUrl;
    @Value("${clients.promotion.url:http://localhost:8085}")
    private String promotionUrl;
    @Value("${clients.user.url:http://localhost:8081}")
    private String userUrl;
    @Value("${scanms.finance.token-url:}")
    private String tokenUrl;
    @Value("${scanms.finance.client-id:order-service}")
    private String clientId;
    @Value("${scanms.finance.client-secret:}")
    private String secret;
    private final RestClient http = FinanceDispatch.client();

    private <T> T tx(Supplier<T> action) {
        return new TransactionTemplate(transactions).execute(s -> action.get());
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(Map<?, ?> response) {
        if (response == null || !(response.get("result") instanceof Map<?, ?> value))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Checkout source contract unavailable");
        return (Map<String, Object>) value;
    }

    private Map<String, Object> post(String url, String token, Object body) {
        return result(http.post().uri(url).headers(h -> h.setBearerAuth(token)).body(body).retrieve().body(Map.class));
    }

    private String serviceToken() {
        if (tokenUrl.isBlank() || secret.isBlank())
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service identity is not configured");
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> value;
        try {
            value = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa thể lấy báo giá: kết nối xác thực nội bộ của dịch vụ đơn hàng chưa sẵn sàng.");
        }
        if (value == null || !(value.get("access_token") instanceof String token))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Order service identity unavailable");
        return token;
    }

    private String verifiedReferral(Line line, String customer, String token) {
        if (line.referralLinkId() == null && line.sourceLivestreamId() == null) return null;
        String path = line.referralLinkId() != null ? "/api/v1/referrals/attribution/" : "/api/v1/referrals/livestream-attribution/";
        String reference = line.referralLinkId() != null ? line.referralLinkId() : line.sourceLivestreamId();
        Map<String, Object> verified;
        try {
            verified = result(http.get().uri(promotionUrl + path + "{id}?productId={productId}", reference, line.productId())
                    .headers(headers -> headers.setBearerAuth(token)).retrieve().body(Map.class));
        } catch (RestClientResponseException ex) {
            if (Set.of(404, 409, 410).contains(ex.getStatusCode().value()))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Liên kết KOL không còn hợp lệ. Vui lòng mở lại liên kết giới thiệu mới.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa xác minh được liên kết KOL. Vui lòng thử lại sau.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa kết nối được dịch vụ hoa hồng. Vui lòng thử lại sau.");
        }
        if (verified.isEmpty() && line.referralLinkId() == null) return null;
        if (!line.productId().equals(verified.get("productId")) || verified.get("collaboratorId") == null || verified.get("referralLinkId") == null
                || (line.referralLinkId() != null && !line.referralLinkId().equals(verified.get("referralLinkId"))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không xác minh được KOL giới thiệu sản phẩm.");
        if (customer.equals(verified.get("userId")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Không tính hoa hồng cho đơn tự mua qua liên kết của mình.");
        return verified.get("referralLinkId").toString();
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> checkout(Input input, Jwt jwt) {
        if (!Set.of("PAYOS", "WALLET", "COD").contains(input.paymentMethod()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported payment method");
        Map<String, Object> user = result(http.get().uri(userUrl + "/api/v1/users").headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class));
        if (!"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active customer profile required");
        String customer = user.get("userId").toString(), serialized = JsonMapper.builder().build().writeValueAsString(input);
        String token = serviceToken();
        shippingPolicy.perStore();
        // Idempotent retries must use the original order's captured policy.
        var existing = orders.findByCustomerIdAndIdempotencyKey(customer, input.idempotencyKey());
        if ((existing.isEmpty() || !"READY".equals(existing.get().getSagaState().get("checkoutState"))) && input.expectedShippingFeeVnd() != null) {
            var quote = post(productUrl + "/api/v1/inventory/reservations/quote", token, Map.of("items", input.items()));
            var quotedLines = (List<Map<String, Object>>) quote.get("lines");
            long expected = shippingPolicy.total(quotedLines.stream().map(l -> l.get("storeId")).distinct().count());
            if (input.expectedShippingFeeVnd() != expected)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Phí vận chuyển đã thay đổi. Vui lòng tính lại trước khi đặt hàng.");
        }
        Order candidate = tx(() -> {
            Optional<Order> previous = orders.findByCustomerIdAndIdempotencyKey(customer, input.idempotencyKey());
            if (previous.isPresent()) {
                if (!serialized.equals(previous.get().getCheckoutRequest()))
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout key belongs to another request");
                return previous.get();
            }
            return orders.saveAndFlush(Order.builder().customerId(customer).idempotencyKey(input.idempotencyKey()).checkoutRequest(serialized).payableVnd(0L).currency("VND").status(OrderStatus.PENDING)
                    .shippingSnapshot(Map.of("name", input.customerName(), "phone", input.customerPhone(), "address", input.shippingAddress()))
                    .sagaState(Map.of("checkoutState", "RESERVING", "paymentMethod", input.paymentMethod())).expiresAt(LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30)).build());
        });
        return tx(() -> {
            Order order = orders.lockById(candidate.getOrderId()).orElseThrow();
            if ("READY".equals(order.getSagaState().get("checkoutState"))) return view(order);
            if (order.getStatus() == OrderStatus.CANCELLED || order.getStatus() == OrderStatus.EXPIRED || !order.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Checkout expired; use a new checkout key");
            Map<Line, String> referrals = new HashMap<>();
            for (Line requested : input.items()) {
                String referral = verifiedReferral(requested, customer, token);
                if (referral != null) referrals.put(requested, referral);
            }
            Map<String, Object> reserved = post(productUrl + "/api/v1/inventory/reservations/" + order.getOrderId(), token, Map.of("items", input.items()));
            List<Map<String, Object>> lines = (List<Map<String, Object>>) reserved.get("lines");
            List<Map<String, Object>> discounts = List.of();
            if (input.couponCode() != null && !input.couponCode().isBlank())
                discounts = (List<Map<String, Object>>) post(promotionUrl + "/api/v1/checkout-vouchers/" + order.getOrderId(), token, Map.of("code", input.couponCode(), "customerId", customer, "lines", lines)).get("allocations");
            Map<String, SellerOrder> sellerMap = new LinkedHashMap<>();
            long total = 0;
            for (Map<String, Object> line : lines) {
                List<Line> matching = input.items().stream().filter(requested -> requested.productId().equals(line.get("productId"))
                        && (requested.variantId() == null || requested.variantId().equals(line.get("variantId")))).toList();
                if (matching.size() != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "Dòng sản phẩm không khớp với yêu cầu đặt hàng.");
                Line requested = matching.getFirst();
                String storeId = line.get("storeId").toString();
                SellerOrder seller = sellerMap.computeIfAbsent(storeId, id -> sellers.saveAndFlush(SellerOrder.builder().orderId(order.getOrderId()).storeId(id).status(SellerOrderStatus.PENDING).totalsSnapshot(new LinkedHashMap<>()).build()));
                long gross = number(line, "grossAmountVnd"), discount = 0;
                List<Map<String, Object>> ds = discounts.stream().filter(d -> Objects.equals(d.get("variantId"), line.get("variantId"))).toList();
                for (var d : ds) discount = Math.addExact(discount, number(d, "allocatedAmountVnd"));
                if (discount < 0 || discount > gross)
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Invalid allocated discount");
                OrderItem item = items.saveAndFlush(OrderItem.builder().sellerOrderId(seller.getSellerOrderId()).productId(line.get("productId").toString()).variantId(line.get("variantId").toString()).sourceLivestreamId(requested.sourceLivestreamId()).referralLinkId(referrals.get(requested)).quantity(((Number) line.get("quantity")).intValue()).unitPrice(number(line, "unitPriceVnd")).grossAmountVnd(gross).discountAmountVnd(discount).netPaidAmountVnd(gross - discount).productSnapshot(line).selectedSize(Objects.toString(line.get("size"), null)).build());
                for (var d : ds)
                    if (number(d, "allocatedAmountVnd") > 0)
                        allocations.save(DiscountAllocation.builder().orderItemId(item.getOrderItemId()).voucherId(d.get("voucherId").toString()).sourceType(DiscountSourceType.VOUCHER).fundedBy(DiscountFundingType.valueOf(d.get("fundedBy").toString())).allocatedAmountVnd(number(d, "allocatedAmountVnd")).ruleSnapshotJson(JsonMapper.builder().build().writeValueAsString(d)).build());
                total = Math.addExact(total, gross - discount);
                Map<String, Object> totals = new LinkedHashMap<>(seller.getTotalsSnapshot());
                totals.put("storeName", line.get("storeName"));
                totals.put("netPaidVnd", Math.addExact(((Number) totals.getOrDefault("netPaidVnd", 0L)).longValue(), gross - discount));
                seller.setTotalsSnapshot(totals);
            }
            long shippingFee = shippingPolicy.perStore();
            for (SellerOrder seller : sellerMap.values()) {
                var totals = new LinkedHashMap<String, Object>(seller.getTotalsSnapshot());
                totals.putAll(shippingPolicy.snapshot());
                totals.put("shippingFeeVnd", shippingFee);
                totals.put("customerTotalVnd", Math.addExact(((Number) totals.get("netPaidVnd")).longValue(), shippingFee));
                seller.setTotalsSnapshot(totals);
            }
            total = Math.addExact(total, shippingPolicy.total(sellerMap.size()));
            if (total <= 0)
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Zero-value checkout requires a separate free-order policy");
            order.setPayableVnd(total);
            order.setStatus(OrderStatus.AWAITING_PAYMENT);
            var saga = new LinkedHashMap<String, Object>(shippingPolicy.snapshot());
            saga.put("checkoutState", "READY");
            saga.put("paymentMethod", input.paymentMethod());
            saga.put("shippingFeeVnd", shippingPolicy.total(sellerMap.size()));
            order.setSagaState(saga);
            orders.saveAndFlush(order);
            return view(order);
        });
    }

    private long number(Map<String, Object> map, String key) {
        return new BigDecimal(map.get(key).toString()).longValueExact();
    }

    public Map<String, Object> view(Order order) {
        List<Map<String, Object>> sellerViews = new ArrayList<>();
        for (SellerOrder s : sellers.findAll().stream().filter(s -> order.getOrderId().equals(s.getOrderId())).toList())
            sellerViews.add(Map.of("storeId", s.getStoreId(), "storeName", s.getTotalsSnapshot().getOrDefault("storeName", "Shop"), "publicOrderCode", order.getOrderId(), "finalAmount", s.getTotalsSnapshot().getOrDefault("customerTotalVnd", s.getTotalsSnapshot().getOrDefault("netPaidVnd", 0)), "shippingFeeVnd", s.getTotalsSnapshot().getOrDefault("shippingFeeVnd", 0), "items", items.findAll().stream().filter(i -> s.getSellerOrderId().equals(i.getSellerOrderId())).map(OrderItem::getProductSnapshot).toList()));
        var response = new LinkedHashMap<String, Object>(Map.of("orderId", order.getOrderId(), "publicOrderCode", order.getOrderId(), "finalAmount", order.getPayableVnd(), "paymentMethod", order.getSagaState().get("paymentMethod"), "paymentStatus", order.getStatus() == OrderStatus.PAID ? "PAID" : "UNPAID", "status", order.getStatus(), "isMultiStore", sellerViews.size() > 1, "orders", sellerViews));
        for (String key : List.of("shippingFeeVnd", "shippingMode", "shippingPayer", "shippingSettlementResponsible", "shippingSettlementStatus"))
            if (order.getSagaState().containsKey(key)) response.put(key, order.getSagaState().get(key));
        return response;
    }

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> quote(QuoteInput input, Jwt jwt) {
        Map<String, Object> user = result(http.get().uri(userUrl + "/api/v1/users").headers(h -> h.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class));
        if (!"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        String token = serviceToken();
        List<Map<String, Object>> lines = (List<Map<String, Object>>) post(productUrl + "/api/v1/inventory/reservations/quote", token, Map.of("items", input.items())).get("lines");
        long gross = 0;
        for (var line : lines) gross = Math.addExact(gross, number(line, "grossAmountVnd"));
        long discount = 0;
        if (input.couponCode() != null && !input.couponCode().isBlank())
            discount = number(post(promotionUrl + "/api/v1/checkout-vouchers/quote", token, Map.of("code", input.couponCode(), "customerId", user.get("userId"), "lines", lines)), "discountAmountVnd");
        long shipping = shippingPolicy.total(lines.stream().map(l -> l.get("storeId")).distinct().count());
        var response = new LinkedHashMap<String, Object>(shippingPolicy.snapshot());
        response.putAll(Map.of("code", Objects.toString(input.couponCode(), ""), "discountAmount", discount, "discountType", "VOUCHER", "subtotalVnd", gross, "shippingFeeVnd", shipping, "finalAmount", Math.addExact(gross - discount, shipping)));
        return response;
    }
}
