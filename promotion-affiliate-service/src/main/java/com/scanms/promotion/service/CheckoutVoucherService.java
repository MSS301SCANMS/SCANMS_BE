package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CheckoutVoucherInput;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface CheckoutVoucherService {
    Map<String, Object> reserve(String orderId, CheckoutVoucherInput input, Jwt jwt);

    Map<String, Object> quote(CheckoutVoucherInput input, Jwt jwt);

    Map<String, Object> release(String orderId, Jwt jwt);

    Map<String, Object> commit(String orderId, Jwt jwt);

    Map<String, Object> restore(String orderId, Jwt jwt);
}
