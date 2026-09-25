package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.dto.response.VoucherResponse;
import com.scanms.promotion.entity.Voucher;
import org.springframework.stereotype.Component;

@Component
public class VoucherMapper {
    public Voucher toEntity(CreateVoucherRequest request) {
        return Voucher.builder()
                .code(request.code())
                .issuerType(request.issuerType())
                .storeId(request.storeId())
                .productId(request.productId())
                .livestreamId(request.livestreamId())
                .scopeType(request.scopeType())
                .discountType(request.discountType())
                .value(request.value())
                .cap(request.cap())
                .minimumSubtotal(request.minimumSubtotal())
                .validFrom(request.validFrom())
                .validUntil(request.validUntil())
                .limits(request.limits())
                .scope(request.scope())
                .funding(request.funding())
                .build();
    }

    public VoucherResponse toResponse(Voucher entity) {
        return new VoucherResponse(
                entity.getVoucherId(),
                entity.getCode(),
                entity.getIssuerType(),
                entity.getStoreId(),
                entity.getProductId(),
                entity.getLivestreamId(),
                entity.getScopeType(),
                entity.getDiscountType(),
                entity.getValue(),
                entity.getCap(),
                entity.getMinimumSubtotal(),
                entity.getValidFrom(),
                entity.getValidUntil(),
                entity.getLimits(),
                entity.getScope(),
                entity.getFunding(),
                entity.getVersion(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
