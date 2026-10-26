package com.scanms.order.service;

import com.scanms.order.constant.ReturnRequestStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface ShopReturnService {
    record ResponseInput(
            @NotBlank
            @Size(max = 5000)
            String response,
            @Size(max = 10)
            List<@NotBlank @Size(max = 2048) String> imageUrls
    ) {
    }

    record View(
            String returnRequestId,
            String orderId,
            String sellerOrderId,
            String orderItemId,
            String storeId,
            String storeName,
            String customerName,
            String customerPhone,
            String productName,
            String productImageUrl,
            Integer quantity,
            Long refundAmountVnd,
            ReturnRequestStatus status,
            String reason,
            Map<String, Object> evidenceRefs,
            Map<String, Object> shopResponse,
            String decisionReason,
            LocalDateTime requestedAt,
            LocalDateTime deliveredAt,
            LocalDateTime returnDeadline,
            LocalDateTime resolvedAt
    ) {
    }

    List<View> list(Jwt jwt);

    List<View> managerList();

    View respond(
            String id,
            ResponseInput input,
            Jwt jwt
    );

    record ManagerDecision(
            @NotNull ReturnRequestStatus status,
            @NotBlank @Size(min = 20, max = 3000) String notes
    ) {
    }

    Map<String, Object> managerDecide(String id, ManagerDecision input);
}
