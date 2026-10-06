package com.scanms.payment.controller;
import com.scanms.payment.service.WithdrawalProcessor;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
/** Optional signed payout notification ingress; REST polling remains required for providers without notifications. */
@RestController @RequiredArgsConstructor
public class PayoutNotificationController {
    private final WithdrawalProcessor processor;
    @PostMapping("/api/v1/payouts/webhooks/payos")
    Map<String,String> notification(@RequestBody Map<String,Object> envelope,@RequestHeader(value="x-signature",required=false) String header) {
        @SuppressWarnings("unchecked") Map<String,Object> data=envelope.get("data") instanceof Map<?,?> map?(Map<String,Object>)map:envelope;
        processor.notification(data,header!=null?header:Objects.toString(envelope.get("signature"),null));
        return Map.of("code","00");
    }
}
