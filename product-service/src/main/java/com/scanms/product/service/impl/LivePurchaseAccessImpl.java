package com.scanms.product.service.impl;

import com.scanms.product.service.LivePurchaseAccess;
import com.scanms.product.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

import tools.jackson.databind.json.JsonMapper;

@Service
public class LivePurchaseAccessImpl implements LivePurchaseAccess {
    @Value("${scanms.live.metrics-secret:${LIVE_METRICS_SECRET:}}")
    private String secret;
    private final JsonMapper json = JsonMapper.builder().build();

    public String issue(String livestreamId, Jwt viewer) {
        if (viewer == null || viewer.getSubject() == null) throw new AppException(ErrorCode.UNAUTHORIZED);
        String value = json.writeValueAsString(java.util.Map.of("live", livestreamId, "sub", viewer.getSubject(), "exp", Instant.now().plusSeconds(120).getEpochSecond()));
        String payload = Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public void verify(String token, String livestreamId, String identitySubject) {
        try {
            if (token == null || token.length() > 3000 || identitySubject == null) throw new IllegalArgumentException();
            String[] parts = token.split("\\.");
            if (parts.length != 2 || !MessageDigest.isEqual(sign(parts[0]), Base64.getUrlDecoder().decode(parts[1])))
                throw new IllegalArgumentException();
            var claims = json.readTree(Base64.getUrlDecoder().decode(parts[0]));
            if (!livestreamId.equals(claims.path("live").asText()) || !identitySubject.equals(claims.path("sub").asText())
                    || claims.path("exp").asLong() <= Instant.now().getEpochSecond())
                throw new IllegalArgumentException();
        } catch (Exception ex) {
            throw new AppException(ErrorCode.CONFLICT, "Hãy mở lại phòng livestream để mua với giá trong live.");
        }
    }

    private byte[] sign(String payload) {
        if (secret == null || secret.length() < 32)
            throw new AppException(ErrorCode.CONFLICT, "Live purchase relay is not configured");
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(("LIVE_PURCHASE\n" + payload).getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
