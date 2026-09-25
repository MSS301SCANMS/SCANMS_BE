package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import java.util.*;

public interface CommissionPolicyService {
    CommissionPolicyResponse create(CreateCommissionPolicyRequest request);
    CommissionPolicyResponse getById(String id);
    List<CommissionPolicyResponse> findAll();
}

