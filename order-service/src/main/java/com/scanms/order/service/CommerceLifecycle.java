package com.scanms.order.service;

import com.scanms.order.entity.SellerOrder;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.List;
import java.util.Map;

public interface CommerceLifecycle {
    record Dispatch(
            @NotBlank
            String environment,
            @Size(max = 100)
            String carrier,
            @Size(max = 100)
            String trackingCode
    ) {}

    record ReturnInput(
            @NotBlank
            String orderItemId,
            @Min(1)
            int quantity,
            @NotBlank
            @Size(max = 128)
            String idempotencyKey,
            @NotBlank
            @Size(max = 100)
            String reason,
            Map<String, Object> evidenceRefs
    ) {}

    SellerOrder confirm(
            String id,
            Jwt jwt
    );

    Map<String, Object> dispatch(
            String id,
            Dispatch input,
            Jwt jwt
    );

    Map<String, Object> track(
            String code,
            Jwt jwt
    );

    Map<String, Object> simulate(
            String id,
            boolean complete,
            Jwt jwt
    );

    Map<String, Object> delivered(
            String id,
            Jwt jwt
    );

    Map<String, Object> receipt(
            String id,
            Jwt jwt
    );

    Map<String, Object> createReturn(
            ReturnInput input,
            Jwt jwt
    );

    Map<String, Object> getReturn(
            String id,
            Jwt jwt
    );

    List<Map<String, Object>> myReturns(Jwt jwt);

    Map<String, Object> cancelReturn(
            String id,
            Jwt jwt
    );

    Map<String, Object> sendReturn(
            String id,
            Dispatch input,
            Jwt jwt
    );

    Map<String, Object> simulateReturn(
            String id,
            String event,
            Jwt jwt
    );

    Map<String, Object> receiveReturn(
            String id,
            Jwt jwt
    );
}
