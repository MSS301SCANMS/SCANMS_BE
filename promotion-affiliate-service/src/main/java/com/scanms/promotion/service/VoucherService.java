package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.dto.response.VoucherResponse;
import java.util.*;

public interface VoucherService {
    VoucherResponse create(CreateVoucherRequest request);
    VoucherResponse getById(UUID id);
    List<VoucherResponse> findAll();
}
