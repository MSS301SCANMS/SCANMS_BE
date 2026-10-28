package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.OrderCancellationRefund;
import com.scanms.order.service.CancellationRefundSource;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/orders")
@RequiredArgsConstructor
public class CancellationRefundController {
    private final CancellationRefundSource source;

    @GetMapping("/{id}/cancellation-refund-basis")
    public ApiResponse<Map<String, Object>> basis(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(source.basis(id, jwt));
    }

    @PostMapping("/{id}/cancellation-refunded")
    public ApiResponse<Map<String, Object>> refunded(@PathVariable String id, @Valid @RequestBody OrderCancellationRefund input, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(source.acknowledge(id, input, jwt));
    }
}
