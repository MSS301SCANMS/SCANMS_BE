package com.scanms.order.service;

import com.scanms.order.dto.request.OrderCancellationDecision;
import com.scanms.order.dto.request.OrderCancellationRequest;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface OrderCancellationService {
    Map<String, Object> request(String orderId, OrderCancellationRequest input, Jwt jwt);

    Map<String, Object> decide(String sellerOrderId, OrderCancellationDecision input, Jwt jwt);
}
