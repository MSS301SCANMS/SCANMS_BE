package com.scanms.payment.controller;

import com.scanms.payment.dto.ApiResponse;
import com.scanms.payment.service.KafkaHealthService;
import com.scanms.payment.service.PaymentOverviewService;
import com.scanms.payment.service.PaymentAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/finance/system")
@RequiredArgsConstructor
public class SystemHealthController {
    private final KafkaHealthService kafka;
    private final PaymentOverviewService overview;
    private final PaymentAccess access;

    /** Chỉ ADMIN/SYSTEM_ADMIN: lộ cluster id, tên topic và consumer group. */
    @GetMapping("/kafka")
    public ApiResponse<Map<String, Object>> kafka() {
        access.systemAdminOnly();
        return ApiResponse.success(kafka.status());
    }

    /** Chỉ ADMIN/SYSTEM_ADMIN: cờ cấu hình (đúng/sai) và số lượng bản ghi tài chính theo trạng thái. */
    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview() {
        access.systemAdminOnly();
        return ApiResponse.success(overview.overview());
    }
}
