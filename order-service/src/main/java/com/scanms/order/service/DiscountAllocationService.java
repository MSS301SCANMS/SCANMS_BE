package com.scanms.order.service;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import java.util.*;

import com.scanms.order.constant.DiscountFundingType;
import com.scanms.order.entity.DiscountAllocation;
import com.scanms.order.entity.OrderItem;

public interface DiscountAllocationService {
    DiscountAllocationResponse create(CreateDiscountAllocationRequest request);
    DiscountAllocationResponse getById(String id);
    List<DiscountAllocationResponse> findAll();
    Map<String, Long> allocateOrderDiscount(List<OrderItem> items, Long totalDiscountVnd);
    List<DiscountAllocation> createAllocations(List<OrderItem> items, String voucherId, DiscountFundingType fundingType, Map<String, Long> itemAllocations);
}

