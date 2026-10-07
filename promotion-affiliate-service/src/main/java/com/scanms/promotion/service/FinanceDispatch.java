package com.scanms.promotion.service;
import com.scanms.promotion.constant.CommissionStatus;
import com.scanms.promotion.repository.CommissionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.http.MediaType;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.RestClient;
import java.util.*;
import java.time.Duration;
@Component @RequiredArgsConstructor
public class FinanceDispatch {
    private final CommissionRepository commissions;
    private int cursor;
    @Value("${scanms.finance.token-url:}") private String tokenUrl;
    @Value("${scanms.finance.client-id:promotion-affiliate-service}") private String clientId;
    @Value("${scanms.finance.client-secret:}") private String secret;
    @Value("${clients.payment.url:http://localhost:8084}") private String paymentUrl;
    private final RestClient http=client();
    private static RestClient client() {
        var factory=new org.springframework.http.client.JdkClientHttpRequestFactory(java.net.http.HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        factory.setReadTimeout(Duration.ofSeconds(15)); return RestClient.builder().requestFactory(factory).build();
    }
    @Scheduled(fixedDelayString="${scanms.finance.dispatch-delay-ms:60000}") public void dispatch() {
        if(tokenUrl.isBlank() || secret.isBlank()) return;
        String token;
        try {
            var form=new LinkedMultiValueMap<String,String>(); form.add("grant_type","client_credentials"); form.add("client_id",clientId); form.add("client_secret",secret);
            Map<?,?> response=http.post().uri(tokenUrl).contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(Map.class);
            if(response==null || !(response.get("access_token") instanceof String value)) return; token=value;
        } catch(Exception ex) { return; }
        var eligible=commissions.findAll().stream().filter(c -> c.getStatus()==CommissionStatus.ELIGIBLE)
                .sorted(Comparator.comparing(com.scanms.promotion.entity.Commission::getCommissionId)).toList();
        if(eligible.isEmpty()) return;
        int start=Math.floorMod(cursor,eligible.size()), count=Math.min(20,eligible.size());
        for(int i=0;i<count;i++) {
            var c=eligible.get((start+i)%eligible.size());
            try { http.post().uri(paymentUrl+"/api/v1/finance/commissions/"+c.getCommissionId()+"/credit").headers(h -> h.setBearerAuth(token)).body(Map.of()).retrieve().toBodilessEntity(); }
            catch(Exception ex) { /* Reference is retried until the source acknowledgement arrives. */ }
        }
        cursor=(start+count)%eligible.size();
    }
}
