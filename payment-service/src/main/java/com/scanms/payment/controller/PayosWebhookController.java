package com.scanms.payment.controller;
import com.scanms.payment.service.PaymentWorkflow;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController @RequiredArgsConstructor
public class PayosWebhookController {
    private final PaymentWorkflow workflow;
    @PostMapping("/api/v1/payments/webhooks/payos") Map<String,String> webhook(@RequestBody Map<String,Object> input) {
        workflow.webhook(input); return Map.of("code","00");
    }
}
