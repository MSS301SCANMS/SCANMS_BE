package com.scanms.product.service;

import com.scanms.product.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Component
public class LiveMetricsSignature {
    @Value("${scanms.live.metrics-secret:${LIVE_METRICS_SECRET:}}") private String secret;
    public void require(String payload,String timestamp,String signature){
        if(secret==null||secret.length()<32)throw new AppException(ErrorCode.CONFLICT,"Live metrics relay is not configured");
        try {
            long seconds=Long.parseLong(timestamp);
            if(Math.abs(Instant.now().getEpochSecond()-seconds)>60)throw new IllegalArgumentException();
            var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));
            var expected=mac.doFinal((payload+"\n"+timestamp).getBytes(StandardCharsets.UTF_8));
            if(!MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature)))throw new IllegalArgumentException();
        }catch(Exception ex){throw new AppException(ErrorCode.FORBIDDEN,"Invalid live metrics relay signature");}
    }
}
