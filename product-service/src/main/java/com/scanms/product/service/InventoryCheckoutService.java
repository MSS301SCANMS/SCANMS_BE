package com.scanms.product.service;

import com.scanms.product.dto.request.InventoryReservationInput;
import java.util.Map;
import org.springframework.security.oauth2.jwt.Jwt;

public interface InventoryCheckoutService {
    Map<String, Object> reserve(String orderId, InventoryReservationInput input, Jwt jwt);

    Map<String, Object> quote(InventoryReservationInput input, Jwt jwt);

    Map<String, Object> release(String orderId, Jwt jwt);

    Map<String, Object> cancel(String orderId, Jwt jwt);

    Map<String, Object> commit(String orderId, Jwt jwt);
}
