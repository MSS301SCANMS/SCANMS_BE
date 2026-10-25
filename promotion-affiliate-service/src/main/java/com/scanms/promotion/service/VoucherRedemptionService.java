package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateVoucherRedemptionRequest;
import com.scanms.promotion.dto.response.VoucherRedemptionResponse;

import java.util.List;

public interface VoucherRedemptionService {
    VoucherRedemptionResponse create(CreateVoucherRedemptionRequest request);

    VoucherRedemptionResponse getById(String id);

    List<VoucherRedemptionResponse> findAll();
}

