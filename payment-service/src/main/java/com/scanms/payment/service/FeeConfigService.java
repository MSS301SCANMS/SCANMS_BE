package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreateFeeConfigRequest;
import com.scanms.payment.dto.response.FeeConfigResponse;
import java.util.*;

public interface FeeConfigService {
    FeeConfigResponse create(CreateFeeConfigRequest request);
    FeeConfigResponse getById(String id);
    List<FeeConfigResponse> findAll();
}

