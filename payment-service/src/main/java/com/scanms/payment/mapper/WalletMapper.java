package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateWalletRequest;
import com.scanms.payment.dto.response.WalletResponse;
import com.scanms.payment.entity.Wallet;
import org.springframework.stereotype.Component;

@Component
public class WalletMapper {
    public Wallet toEntity(CreateWalletRequest request) {
        return Wallet.builder()
                .ownerType(request.ownerType())
                .ownerRefId(request.ownerRefId())
                .currency(request.currency())
                .availableBalanceVnd(request.availableBalanceVnd())
                .heldBalanceVnd(request.heldBalanceVnd())
                .status(request.status())
                .build();
    }
    public WalletResponse toResponse(Wallet entity) {
        return new WalletResponse(
                entity.getWalletId(),
                entity.getOwnerType(),
                entity.getOwnerRefId(),
                entity.getCurrency(),
                entity.getAvailableBalanceVnd(),
                entity.getHeldBalanceVnd(),
                entity.getStatus(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}

