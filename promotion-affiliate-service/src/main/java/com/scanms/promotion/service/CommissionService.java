package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateCommissionRequest;
import com.scanms.promotion.dto.response.CommissionResponse;
import java.util.*;

public interface CommissionService {
    CommissionResponse create(CreateCommissionRequest request);
    CommissionResponse getById(String id);
    List<CommissionResponse> findAll();
}
