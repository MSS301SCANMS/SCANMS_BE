package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreatePaymentRequest;
import com.scanms.payment.dto.response.PaymentResponse;
import com.scanms.payment.entity.Payment;
import org.springframework.stereotype.Component;

@Component
public class PaymentMapper {
    public Payment toEntity(CreatePaymentRequest request) {
        return Payment.builder()
                .orderId(request.orderId())
                .providerCode(request.providerCode())
                .transactionId(request.transactionId())
                .amountVnd(request.amountVnd())
                .currency(request.currency())
                .status(request.status())
                .idempotencyKey(request.idempotencyKey())
                .verifiedAt(request.verifiedAt())
                .failureReason(request.failureReason())
                .build();
    }

    public PaymentResponse toResponse(Payment entity) {
        return new PaymentResponse(
                entity.getPaymentId(),
                entity.getOrderId(),
                entity.getProviderCode(),
                entity.getTransactionId(),
                entity.getAmountVnd(),
                entity.getCurrency(),
                entity.getStatus(),
                entity.getIdempotencyKey(),
                entity.getVerifiedAt(),
                entity.getFailureReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
