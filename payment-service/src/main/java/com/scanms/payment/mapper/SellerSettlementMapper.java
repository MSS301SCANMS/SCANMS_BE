package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.CreateSellerSettlementRequest;
import com.scanms.payment.dto.response.SellerSettlementResponse;
import com.scanms.payment.entity.SellerSettlement;
import org.springframework.stereotype.Component;

@Component
public class SellerSettlementMapper {
    public SellerSettlement toEntity(CreateSellerSettlementRequest request) {
        return SellerSettlement.builder()
                .storeId(request.storeId())
                .bankAccountId(request.bankAccountId())
                .walletId(request.walletId())
                .walletTransactionId(request.walletTransactionId())
                .destinationSnapshot(request.destinationSnapshot())
                .orderRefs(request.orderRefs())
                .breakdown(request.breakdown())
                .netAmountVnd(request.netAmountVnd())
                .status(request.status())
                .transferReference(request.transferReference())
                .paidAt(request.paidAt())
                .build();
    }

    public SellerSettlementResponse toResponse(SellerSettlement entity) {
        return new SellerSettlementResponse(
                entity.getSettlementId(),
                entity.getStoreId(),
                entity.getBankAccountId(),
                entity.getWalletId(),
                entity.getWalletTransactionId(),
                entity.getDestinationSnapshot(),
                entity.getOrderRefs(),
                entity.getBreakdown(),
                entity.getNetAmountVnd(),
                entity.getStatus(),
                entity.getTransferReference(),
                entity.getPaidAt(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
