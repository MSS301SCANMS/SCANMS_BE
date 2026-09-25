package com.scanms.order.service;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import java.util.*;

public interface DiscountAllocationService {
    DiscountAllocationResponse create(CreateDiscountAllocationRequest request);
    DiscountAllocationResponse getById(String id);
    List<DiscountAllocationResponse> findAll();
}

