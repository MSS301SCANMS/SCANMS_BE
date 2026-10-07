package com.scanms.payment.service;

import com.scanms.payment.entity.Payment;
import com.scanms.payment.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.net.URLEncoder;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PayosProvider {
    @Value("${scanms.payment.payos.base-url:https://api-merchant.payos.vn}") private String baseUrl;
    @Value("${scanms.payment.payos.client-id:}") private String clientId;
    @Value("${scanms.payment.payos.api-key:}") private String apiKey;
    @Value("${scanms.payment.payos.checksum-key:}") private String checksum;
    @Value("${scanms.payment.payos.payout-client-id:}") private String payoutClient;
    @Value("${scanms.payment.payos.payout-api-key:}") private String payoutApiKey;
    @Value("${scanms.payment.payos.payout-checksum-key:}") private String payoutChecksum;
    @Value("${scanms.payment.frontend-url:http://localhost:5173}") private String frontendUrl;
    public boolean available() { return !clientId.isBlank() && !apiKey.isBlank() && !checksum.isBlank(); }
    public boolean payoutAvailable() { return !payoutClient.isBlank() && !payoutApiKey.isBlank() && !payoutChecksum.isBlank(); }
    private RestClient client(boolean payout) {
        if (payout ? !payoutAvailable() : !available()) throw new AppException(ErrorCode.CONFLICT, "Payment provider is not configured");
        return PaymentHttp.builder().baseUrl(baseUrl).defaultHeader("x-client-id", payout ? payoutClient : clientId)
                .defaultHeader("x-api-key", payout ? payoutApiKey : apiKey).build();
    }
    @SuppressWarnings("unchecked") private Map<String,Object> data(Map<?,?> envelope) {
        if (envelope == null || !"00".equals(envelope.get("code")) || !(envelope.get("data") instanceof Map))
            throw new AppException(ErrorCode.CONFLICT, "Provider has not confirmed this operation");
        return (Map<String,Object>)envelope.get("data");
    }
    public Map<String,Object> createLink(Payment p) {
        Map<String,Object> body = new TreeMap<>();
        String back = frontendUrl + "/payment/payos-return?payment=" + p.getPaymentId();
        body.put("amount", p.getAmountVnd()); body.put("cancelUrl", back + "&cancel=true");
        body.put("description", "SC" + p.getProviderOrderCode().toString().substring(0,7));
        body.put("orderCode", p.getProviderOrderCode()); body.put("returnUrl", back);
        body.put("signature", signature(body, checksum, false));
        body.put("expiredAt",p.getExpiresAt().toEpochSecond(java.time.ZoneOffset.UTC));
        return data(client(false).post().uri("/v2/payment-requests").body(body).retrieve().body(Map.class));
    }
    public Map<String,Object> paymentStatus(long code) { return data(client(false).get().uri("/v2/payment-requests/{id}",code).retrieve().body(Map.class)); }
    public Map<String,Object> cancel(long code) {
        return data(client(false).post().uri("/v2/payment-requests/{id}/cancel",code).body(Map.of("cancellationReason","Customer cancelled")).retrieve().body(Map.class));
    }
    public void verifyWebhook(Map<String,Object> payload, String sig) {
        if (!available() || sig == null || !MessageDigest.isEqual(signature(payload,checksum,false).getBytes(StandardCharsets.US_ASCII),sig.getBytes(StandardCharsets.US_ASCII)))
            throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid provider signature");
    }
    public void verifyPayoutNotification(Map<String,Object> payload, String sig) {
        if (!payoutAvailable() || sig == null || !MessageDigest.isEqual(signature(payload,payoutChecksum,true).getBytes(StandardCharsets.US_ASCII),sig.getBytes(StandardCharsets.US_ASCII)))
            throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid payout notification signature");
    }
    public Map<String,Object> transfer(String reference, long amount, String bankCode, String number) {
        Map<String,Object> body = new TreeMap<>(Map.of("referenceId",reference,"amount",amount,"description","SCANMS withdrawal","toBin",bankCode,"toAccountNumber",number));
        return data(client(true).post().uri("/v1/payouts").header("x-idempotency-key",reference)
                .header("x-signature",signature(body,payoutChecksum,true)).body(body).retrieve().body(Map.class));
    }
    public Map<String,Object> payoutStatus(String id) { return data(client(true).get().uri("/v1/payouts/{id}",id).retrieve().body(Map.class)); }
    public Map<String,Object> findPayout(String reference) {
        Map<String,Object> result = data(client(true).get().uri(b -> b.path("/v1/payouts").queryParam("referenceId",reference).build()).retrieve().body(Map.class));
        Object rows = result.get("payouts");
        if(rows==null) rows=result.get("items");
        Collection<?> records;
        if(rows instanceof List<?> list) records=list;
        else if(rows instanceof Map<?,?> map) records=map.values();
        else throw new AppException(ErrorCode.CONFLICT,"Unrecognized provider payout list; cannot safely retry");
        for(Object row:records) if(row instanceof Map<?,?> m && reference.equals(m.get("referenceId"))) {
            @SuppressWarnings("unchecked") Map<String,Object> found=(Map<String,Object>)m; return found;
        }
        return Map.of();
    }
    public static String signature(Map<String,Object> data, String secret, boolean uriEncode) {
        String canonical = new TreeMap<>(data).entrySet().stream().map(e -> e.getKey()+"="+(uriEncode ? encode(canonicalValue(e.getValue())) : canonicalValue(e.getValue()))).collect(Collectors.joining("&"));
        try {
            Mac mac = Mac.getInstance("HmacSHA256"); mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) { throw new AppException(ErrorCode.CONFLICT,"Provider signing key unavailable"); }
    }
    private static String canonicalValue(Object value) {
        if(value==null || "null".equals(value) || "undefined".equals(value)) return "";
        if(value instanceof Map<?,?> || value instanceof Collection<?>) {
            try { return tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(sorted(value)); }
            catch(Exception ex) { throw new AppException(ErrorCode.INVALID_REQUEST,"Invalid provider signature data"); }
        }
        return value.toString();
    }
    private static Object sorted(Object value) {
        if(value instanceof Map<?,?> map) { Map<String,Object> result=new TreeMap<>(); map.forEach((k,v)->result.put(k.toString(),sorted(v))); return result; }
        if(value instanceof Collection<?> list) return list.stream().map(PayosProvider::sorted).toList();
        return value;
    }
    // Match JavaScript encodeURI for the flat payout request defined by payOS.
    private static String encode(String text) {
        String result = URLEncoder.encode(text,StandardCharsets.UTF_8).replace("+","%20");
        String reserved=";/?:@&=+$,#!'()";
        for(char c:reserved.toCharArray()) result=result.replace("%"+HexFormat.of().withUpperCase().toHexDigits((byte)c),Character.toString(c));
        return result.replace("%7E","~");
    }
}
