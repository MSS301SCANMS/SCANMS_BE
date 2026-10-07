package com.scanms.order.service;

import com.scanms.order.constant.*;
import com.scanms.order.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.time.*;
import java.util.*;

/** Source commands are derived from persisted business state; Payment deduplicates every reference. */
@Component @RequiredArgsConstructor
public class FinanceDispatch {
    private final ReturnRequestRepository returns;
    private final SellerOrderRepository sellers;
    private final OrderItemRepository items;
    private final OrderRepository orders;
    private final org.springframework.transaction.PlatformTransactionManager transactions;
    @Value("${scanms.finance.token-url:}") private String tokenUrl;
    @Value("${scanms.finance.client-id:order-service}") private String clientId;
    @Value("${scanms.finance.client-secret:}") private String secret;
    @Value("${clients.payment.url:http://localhost:8084}") private String paymentUrl;
    @Value("${clients.promotion.url:http://localhost:8085}") private String promotionUrl;
    @Value("${clients.product.url:http://localhost:8082}") private String productUrl;
    private int refundCursor=0,settlementCursor=0,checkoutCursor=0;
    private <T> List<T> batch(List<T> values,int cursor) {
        if(values.isEmpty()) return List.of(); int start=Math.floorMod(cursor,values.size());
        var result=new ArrayList<T>(); for(int i=0;i<Math.min(20,values.size());i++) result.add(values.get((start+i)%values.size())); return result;
    }
    private final RestClient http=client();
    static RestClient client() {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15)); return RestClient.builder().requestFactory(factory).build();
    }
    private String token() {
        if(tokenUrl.isBlank() || secret.isBlank()) return null;
        var form=new LinkedMultiValueMap<String,String>(); form.add("grant_type","client_credentials"); form.add("client_id",clientId); form.add("client_secret",secret);
        Map<?,?> result=http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
        return result==null?null:Objects.toString(result.get("access_token"),null);
    }
    private Map<?,?> post(String url,String token,Object body) {
        return http.post().uri(url).headers(h -> h.setBearerAuth(token)).body(body).retrieve().body(Map.class);
    }
    @Scheduled(fixedDelayString="${scanms.finance.dispatch-delay-ms:60000}")
    public void dispatch() {
        String token;
        try { token=token(); } catch(Exception ex) { return; }
        if(token==null) return;
        for(var r:batch(returns.findAll().stream().filter(r -> Set.of(ReturnRequestStatus.APPROVED,ReturnRequestStatus.RECEIVED,ReturnRequestStatus.REFUND_PENDING).contains(r.getStatus())).sorted(Comparator.comparing(com.scanms.order.entity.ReturnRequest::getReturnRequestId)).toList(),refundCursor))
            try { post(paymentUrl+"/api/v1/finance/refunds/"+r.getReturnRequestId(),token,Map.of()); } catch(Exception ex) { /* Retry persisted source reference next cycle. */ }
        refundCursor+=20;
        for(var seller:batch(sellers.findAll().stream().filter(s -> s.getStatus()==SellerOrderStatus.COMPLETED && s.getReturnDeadline()!=null && s.getReturnDeadline().isBefore(LocalDateTime.now(ZoneOffset.UTC))).sorted(Comparator.comparing(com.scanms.order.entity.SellerOrder::getSellerOrderId)).toList(),settlementCursor)) {
            try {
                for(var item:items.findAll().stream().filter(i -> seller.getSellerOrderId().equals(i.getSellerOrderId())).toList())
                    post(promotionUrl+"/api/v1/finance/commission-finalizations/"+item.getOrderItemId(),token,Map.of("reason","Return window closed; finalize source commission"));
                post(paymentUrl+"/api/v1/finance/settlements",token,Map.of("sellerOrderId",seller.getSellerOrderId()));
            } catch(Exception ex) { /* Ineligible/unresolved sources remain blocked; no money is invented. */ }
        }
        settlementCursor+=20;
        for(var candidate:batch(orders.findAll().stream().filter(o -> o.getCheckoutRequest()!=null && o.getSagaState()!=null && !Boolean.TRUE.equals(o.getSagaState().get("resourcesFinalized"))).sorted(Comparator.comparing(com.scanms.order.entity.Order::getOrderId)).toList(),checkoutCursor)) {
            try {
                var order=new org.springframework.transaction.support.TransactionTemplate(transactions).execute(s -> {
                    var current=orders.lockById(candidate.getOrderId()).orElseThrow();
                    if(Set.of(OrderStatus.PENDING,OrderStatus.AWAITING_PAYMENT).contains(current.getStatus()) && current.getExpiresAt()!=null && !current.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) current.setStatus(OrderStatus.EXPIRED);
                    return orders.saveAndFlush(current);
                });
                if(order==null) continue;
                String action=Set.of(OrderStatus.CANCELLED,OrderStatus.EXPIRED).contains(order.getStatus())?"release":Set.of(OrderStatus.PAID,OrderStatus.PROCESSING,OrderStatus.COMPLETED).contains(order.getStatus())?"commit":null;
                if(action==null) continue;
                post(productUrl+"/api/v1/inventory/reservations/"+order.getOrderId()+"/"+action,token,Map.of());
                post(promotionUrl+"/api/v1/checkout-vouchers/"+order.getOrderId()+"/"+action,token,Map.of());
                new org.springframework.transaction.support.TransactionTemplate(transactions).executeWithoutResult(s -> {
                    var current=orders.lockById(order.getOrderId()).orElseThrow(); var saga=new LinkedHashMap<>(current.getSagaState()); saga.put("resourcesFinalized",true); saga.put("resourceOutcome",action); current.setSagaState(saga); orders.save(current);
                });
            } catch(Exception ex) { /* Both commands are idempotent, and recovery remains persisted on the order. */ }
        }
        checkoutCursor+=20;
    }
}
