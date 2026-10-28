package com.scanms.order.service;

import com.scanms.order.dto.request.OrderCancellationRefund;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface CancellationRefundSource {
    Map<String, Object> basis(String orderId, Jwt jwt);

    Map<String, Object> acknowledge(String orderId, OrderCancellationRefund input, Jwt jwt);
}
