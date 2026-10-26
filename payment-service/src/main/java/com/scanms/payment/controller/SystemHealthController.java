package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.service.KafkaHealthService;
import com.scanms.payment.service.PaymentAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/system")
@RequiredArgsConstructor
public class SystemHealthController {
    private final KafkaHealthService kafka;
    private final PaymentAccess access;

    /** Chỉ admin/manager: lộ cluster id và tên consumer group. */
    @GetMapping("/kafka")
    public ApiResponse<Map<String, Object>> kafka() {
        access.operatorOnly();
        return ApiResponse.success(kafka.status());
    }
}
