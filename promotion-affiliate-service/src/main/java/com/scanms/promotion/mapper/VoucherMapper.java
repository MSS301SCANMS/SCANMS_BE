package com.scanms.promotion.mapper;

import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.dto.response.VoucherResponse;
import com.scanms.promotion.entity.Voucher;
import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface VoucherMapper {
    Voucher toEntity(CreateVoucherRequest request);
    VoucherResponse toResponse(Voucher entity);
}
