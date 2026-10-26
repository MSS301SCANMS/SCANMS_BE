package com.scanms.order.service;

import com.scanms.order.dto.request.PaidInput;
import com.scanms.order.dto.request.RefundInput;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface FinanceContractService {
    Map<String, Object> paid(String id, PaidInput input, Jwt jwt);

    Map<String, Object> refundBasis(String id, Jwt jwt);

    Map<String, Object> unapplied(String id, String paymentId, Jwt jwt);

    Map<String, Object> refunded(String id, RefundInput input, Jwt jwt);

    Map<String, Object> settlementBasis(String id, Jwt jwt);

    Map<String, Object> commissionItem(String id, Jwt jwt);
}
