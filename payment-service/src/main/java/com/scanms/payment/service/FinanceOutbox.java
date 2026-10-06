package com.scanms.payment.service;

import com.scanms.payment.entity.FinanceEvent;
import com.scanms.payment.repository.FinanceEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.time.Instant;
import java.util.*;

@Service @RequiredArgsConstructor
public class FinanceOutbox {
    private final FinanceEventRepository repository;
    private final PlatformTransactionManager transactions;
    private final PaymentAccess access;
    private final com.scanms.payment.repository.PaymentRepository payments;
    @Value("${scanms.payment.service-token-url:}") private String tokenUrl;
    @Value("${scanms.payment.service-client-id:payment-service}") private String clientId;
    @Value("${scanms.payment.service-client-secret:}") private String secret;
    public boolean configured() { return !tokenUrl.isBlank() && !secret.isBlank(); }
    public Map<String,Object> pending(int page) {
        access.operatorOnly();
        if(page<0) throw new com.scanms.payment.exception.AppException(com.scanms.payment.exception.ErrorCode.INVALID_REQUEST);
        var rows=repository.findByDeliveredAtIsNull(org.springframework.data.domain.PageRequest.of(page,20,org.springframework.data.domain.Sort.by("createdAt")));
        return Map.of("configured",configured(),"total",rows.getTotalElements(),"items",rows.map(e -> {
            Map<String,Object> row=new LinkedHashMap<>(); row.put("eventId",e.getEventId()); row.put("eventKey",e.getEventKey());
            row.put("attempts",e.getAttempts()); row.put("createdAt",e.getCreatedAt()); row.put("nextAttemptAt",e.getNextAttemptAt());
            row.put("lastError",e.getLastError()); row.put("lastHttpStatus",e.getLastHttpStatus()); row.put("blocked",e.isBlocked()); return row;
        }).getContent());
    }
    public void retry(String id) {
        access.operatorOnly();
        new TransactionTemplate(transactions).executeWithoutResult(s -> {
            FinanceEvent e=repository.lockById(id).orElseThrow(() -> new com.scanms.payment.exception.AppException(com.scanms.payment.exception.ErrorCode.RESOURCE_NOT_FOUND));
            if(e.getDeliveredAt()==null) { e.setBlocked(false); e.setNextAttemptAt(Instant.now()); repository.save(e); }
        });
    }
    public void enqueue(String key, String destination, Map<String,Object> payload) {
        if (!repository.existsByEventKey(key)) repository.save(FinanceEvent.builder().eventKey(key).destination(destination)
                .payload(payload).createdAt(Instant.now()).nextAttemptAt(Instant.now()).build());
    }
    public Optional<String> serviceToken() {
        if(!configured()) return Optional.empty();
        var client = PaymentHttp.client();
        var form = new LinkedMultiValueMap<String,String>();
        form.add("grant_type", "client_credentials"); form.add("client_id", clientId); form.add("client_secret", secret);
        try {
            Map<?,?> result = client.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if(result==null || !(result.get("access_token") instanceof String token) || token.isBlank()) return Optional.empty();
            return Optional.of(token);
        } catch (Exception ex) { return Optional.empty(); }
    }
    @Scheduled(fixedDelayString="${scanms.payment.outbox-delay-ms:10000}")
    public void dispatch() {
        Optional<String> credential=serviceToken(); if(credential.isEmpty()) return;
        String token=credential.get(); var client=PaymentHttp.client();
        for (var candidate : repository.findTop20ByDeliveredAtIsNullAndBlockedFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant.now())) {
            new TransactionTemplate(transactions).executeWithoutResult(status -> {
                var event = repository.lockById(candidate.getEventId()).orElseThrow();
                if (event.getDeliveredAt() != null || event.isBlocked()) return;
                var payment=event.getEventKey().startsWith("paid:") ? payments.lockById(event.getEventKey().substring(5)).orElse(null) : null;
                if(payment!=null && "REFUNDED".equals(payment.getOrderSyncStatus())) { event.setDeliveredAt(Instant.now()); repository.save(event); return; }
                try {
                    client.post().uri(event.getDestination()).headers(h -> h.setBearerAuth(token)).body(event.getPayload()).retrieve().toBodilessEntity();
                    event.setDeliveredAt(Instant.now());
                    event.setLastError(null); event.setLastHttpStatus(null);
                    if(payment!=null) { payment.setOrderSyncStatus("SYNCED"); payments.save(payment); }
                } catch (Exception ex) {
                    event.setAttempts(event.getAttempts() + 1);
                    if(ex instanceof org.springframework.web.client.RestClientResponseException response) {
                        event.setLastHttpStatus(response.getStatusCode().value());
                        String detail=response.getResponseBodyAsString(); event.setLastError(detail.substring(0,Math.min(detail.length(),1000)));
                        if(response.getStatusCode().value()==409 || response.getStatusCode().value()==422) {
                            event.setBlocked(true);
                            if(payment!=null) { payment.setOrderSyncStatus("RECONCILIATION_REQUIRED"); payments.save(payment); }
                        }
                    } else { event.setLastHttpStatus(null); event.setLastError("Destination unavailable; retry scheduled"); }
                    event.setNextAttemptAt(Instant.now().plusSeconds(Math.min(3600, 10L * (1L << Math.min(8,event.getAttempts())))));
                }
                repository.save(event);
            });
        }
    }
}
