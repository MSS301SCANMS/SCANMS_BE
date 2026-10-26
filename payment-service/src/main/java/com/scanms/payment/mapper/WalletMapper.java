package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWalletRequest;
import com.scanms.payment.dto.response.WalletResponse;
import com.scanms.payment.entity.Wallet;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface WalletMapper {
    Wallet toEntity(CreateWalletRequest request);
    WalletResponse toResponse(Wallet entity);
}
