package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;
import com.scanms.promotion.exception.*;
import com.scanms.promotion.mapper.VoucherRedemptionMapper;
import com.scanms.promotion.repository.VoucherRedemptionRepository;
import com.scanms.promotion.service.VoucherRedemptionService;
import com.scanms.promotion.service.VoucherAccess;
import com.scanms.promotion.repository.VoucherRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import com.scanms.promotion.entity.VoucherRedemption;

@Service
@RequiredArgsConstructor
@Transactional
public class VoucherRedemptionServiceImpl implements VoucherRedemptionService {
    private final VoucherRedemptionRepository repository;
    private final VoucherRedemptionMapper mapper;
    private final VoucherRepository vouchers;
    private final VoucherAccess access;
    public VoucherRedemptionResponse create(CreateVoucherRedemptionRequest request) {
        throw new AppException(ErrorCode.FORBIDDEN, "Voucher redemptions are created by checkout only");
    }
    @Transactional(readOnly = true)
    public VoucherRedemptionResponse getById(String id) {
        var redemption = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "VoucherRedemption not found: " + id));
        if (!canView(redemption)) throw new AppException(ErrorCode.FORBIDDEN);
        return mapper.toResponse(redemption);
    }
    @Transactional(readOnly = true)
    public List<VoucherRedemptionResponse> findAll() {
        access.userId();
        return repository.findAll().stream().filter(this::canView).map(mapper::toResponse).toList();
    }

    private boolean canView(VoucherRedemption redemption) {
        if (access.isManager() || redemption.getUserId().equals(access.userId())) return true;
        return vouchers.findById(redemption.getVoucherId())
                .map(voucher -> access.ownsStore(voucher.getStoreId())).orElse(false);
    }
}

