package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.service.CancellationRefundService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/order-cancellations")
@RequiredArgsConstructor
public class CancellationRefundController {
    private final CancellationRefundService service;

    @PostMapping("/{id}/refund")
    public ApiResponse<Map<String, Object>> refund(@PathVariable String id) {
        return ApiResponse.success(service.refund(id));
    }
}
