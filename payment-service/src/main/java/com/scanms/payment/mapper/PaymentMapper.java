package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreatePaymentRequest;
import com.scanms.payment.dto.response.PaymentResponse;
import com.scanms.payment.entity.Payment;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    Payment toEntity(CreatePaymentRequest request);
    PaymentResponse toResponse(Payment entity);
}
