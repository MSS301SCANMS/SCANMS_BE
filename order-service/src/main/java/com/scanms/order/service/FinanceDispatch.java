package com.scanms.order.service;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.ReturnRequestStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.ReturnRequest;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.repository.SellerOrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

/**
 * Source commands are derived from persisted business state; Payment deduplicates every reference.
 */
@Component
@RequiredArgsConstructor
public class FinanceDispatch {
    private final ReturnRequestRepository returns;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final PlatformTransactionManager transactions;
    @Value("${scanms.finance.token-url:}")
    private String tokenUrl;
    @Value("${scanms.finance.client-id:order-service}")
    private String clientId;
    @Value("${scanms.finance.client-secret:}")
    private String secret;
    @Value("${clients.payment.url:http://localhost:8084}")
    private String paymentUrl;
    @Value("${clients.promotion.url:http://localhost:8085}")
    private String promotionUrl;
    @Value("${clients.product.url:http://localhost:8082}")
    private String productUrl;
    private int refundCursor = 0, settlementCursor = 0, checkoutCursor = 0, commissionCursor = 0;

    private <T> List<T> batch(List<T> values, int cursor) {
        if (values.isEmpty()) return List.of();
        int start = Math.floorMod(cursor, values.size());
        var result = new ArrayList<T>();
        for (int i = 0; i < Math.min(20, values.size()); i++) result.add(values.get((start + i) % values.size()));
        return result;
    }

    private final RestClient http = client();

    public static RestClient client() {
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().requestFactory(factory).build();
    }

    private String token() {
        if (tokenUrl.isBlank() || secret.isBlank()) return null;
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", clientId);
        form.add("client_secret", secret);
        Map<?, ?> result = http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        return result == null ? null : Objects.toString(result.get("access_token"), null);
    }

    private Map<?, ?> post(String url, String token, Object body) {
        return http.post().uri(url).headers(h -> h.setBearerAuth(token)).body(body).retrieve().body(Map.class);
    }

    private boolean usesVoucher(String checkoutRequest) {
        var request = JsonMapper.builder().build().readTree(checkoutRequest);
        var code = request.path("couponCode");
        if (code.isMissingNode() || code.isNull()) code = request.path("voucherCode");
        var shipping=request.path("shippingCouponCode");
        return code.isTextual() && !code.asText().isBlank() || shipping.isTextual() && !shipping.asText().isBlank();
    }

    @Scheduled(fixedDelayString = "${scanms.finance.dispatch-delay-ms:60000}")
    public void dispatch() {
        String token;
        try {
            token = token();
        } catch (Exception ex) {
            return;
        }
        if (token == null) return;
        // Payment acknowledgements can arrive while Affiliate is unavailable; retry persisted paid items.
        var paidSellerIds=sellers.findAll().stream().filter(s -> orders.findById(s.getOrderId())
                .map(o -> Set.of(OrderStatus.PAID,OrderStatus.PROCESSING,OrderStatus.COMPLETED).contains(o.getStatus())).orElse(false))
                .map(SellerOrder::getSellerOrderId).collect(java.util.stream.Collectors.toSet());
        var attributed=items.findAll().stream().filter(i -> i.getReferralLinkId()!=null && paidSellerIds.contains(i.getSellerOrderId()))
                .sorted(Comparator.comparing(com.scanms.order.entity.OrderItem::getOrderItemId)).toList();
        for(var item:batch(attributed,commissionCursor)) {
            try { post(promotionUrl+"/api/v1/finance/commission-initializations/"+item.getOrderItemId(),token,Map.of("reason","Recover paid referral commission")); }
            catch(Exception ex) { /* Same item reference is retried next cycle without duplicating commission. */ }
        }
        commissionCursor+=20;
        for (var r : batch(returns.findAll().stream().filter(r -> Set.of(ReturnRequestStatus.RECEIVED, ReturnRequestStatus.REFUND_PENDING, ReturnRequestStatus.REFUNDED).contains(r.getStatus())).sorted(Comparator.comparing(ReturnRequest::getReturnRequestId)).toList(), refundCursor)) {
            try {
                if (r.getStatus() != ReturnRequestStatus.REFUNDED)
                    post(paymentUrl + "/api/v1/finance/refunds/" + r.getReturnRequestId(), token, Map.of());
                // Return/refund → ADJUSTED commission theo basis còn lại
                if (r.getStatus() == ReturnRequestStatus.REFUNDED && r.getOrderItemId() != null)
                    post(promotionUrl + "/api/v1/finance/commission-adjustments/" + r.getOrderItemId(), token, Map.of("reason", "Return/refund adjustment for " + r.getReturnRequestId()));
            } catch (Exception ex) { /* Retry persisted source reference next cycle. */ }
        }
        refundCursor += 20;
        for (var seller : batch(sellers.findAll().stream().filter(s -> s.getStatus() == SellerOrderStatus.COMPLETED && s.getReturnDeadline() != null && s.getReturnDeadline().isBefore(LocalDateTime.now(ZoneOffset.UTC))).sorted(Comparator.comparing(SellerOrder::getSellerOrderId)).toList(), settlementCursor)) {
            try {
                for (var item : items.findAll().stream().filter(i -> seller.getSellerOrderId().equals(i.getSellerOrderId())).toList())
                    post(promotionUrl + "/api/v1/finance/commission-finalizations/" + item.getOrderItemId(), token, Map.of("reason", "Return window closed; finalize source commission"));
                post(paymentUrl + "/api/v1/finance/settlements", token, Map.of("sellerOrderId", seller.getSellerOrderId()));
            } catch (Exception ex) { /* Ineligible/unresolved sources remain blocked; no money is invented. */ }
        }
        settlementCursor += 20;
        for (var candidate : batch(orders.findAll().stream().filter(o -> o.getCheckoutRequest() != null && o.getSagaState() != null
                && (!Boolean.TRUE.equals(o.getSagaState().get("resourcesFinalized"))
                || o.getStatus()==OrderStatus.CANCELLED&&!"cancel".equals(o.getSagaState().get("resourceOutcome"))))
                .sorted(Comparator.comparing(Order::getOrderId)).toList(), checkoutCursor)) {
            try {
                var order = new TransactionTemplate(transactions).execute(s -> {
                    var current = orders.lockById(candidate.getOrderId()).orElseThrow();
                    if (Set.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT).contains(current.getStatus()) && current.getExpiresAt() != null && !current.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
                        current.setStatus(OrderStatus.EXPIRED);
                    return orders.saveAndFlush(current);
                });
                if (order == null) continue;
                String action = order.getStatus()==OrderStatus.CANCELLED?"cancel":order.getStatus()==OrderStatus.EXPIRED?"release": Set.of(OrderStatus.PAID, OrderStatus.PROCESSING, OrderStatus.COMPLETED).contains(order.getStatus()) ? "commit" : null;
                if (action == null) continue;
                post(productUrl + "/api/v1/inventory/reservations/" + order.getOrderId() + "/" + action, token, Map.of());
                if (usesVoucher(order.getCheckoutRequest()))
                    post(promotionUrl + "/api/v1/checkout-vouchers/" + order.getOrderId() + "/" + action, token, Map.of());
                new TransactionTemplate(transactions).executeWithoutResult(s -> {
                    var current = orders.lockById(order.getOrderId()).orElseThrow();
                    var saga = new LinkedHashMap<>(current.getSagaState());
                    saga.put("resourcesFinalized", true);
                    saga.put("resourceOutcome", action);
                    current.setSagaState(saga);
                    orders.save(current);
                });
            } catch (Exception ex) { /* Both commands are idempotent, and recovery remains persisted on the order. */ }
        }
        checkoutCursor += 20;
    }
}
