package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.response.WalletTransactionResponse;
import com.scanms.payment.entity.WalletTransaction;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletTransactionMapper {
    WalletTransaction toEntity(CreateWalletTransactionRequest request);
    WalletTransactionResponse toResponse(WalletTransaction entity);
}
