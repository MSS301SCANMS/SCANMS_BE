package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWithdrawalRequest;
import com.scanms.payment.dto.response.WithdrawalRequestResponse;
import com.scanms.payment.entity.WithdrawalRequest;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WithdrawalRequestMapper {
    WithdrawalRequest toEntity(CreateWithdrawalRequest request);
    WithdrawalRequestResponse toResponse(WithdrawalRequest entity);
}
