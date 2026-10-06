package com.scanms.order.service;

import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import java.util.*;

import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.response.SellerOrderSummaryResponse;

public interface SellerOrderService {
    SellerOrderResponse create(CreateSellerOrderRequest request);
    SellerOrderResponse getById(String id);
    List<SellerOrderResponse> findAll();
    List<SellerOrderResponse> findByStore(String storeId, SellerOrderStatus status);
    SellerOrderSummaryResponse getSummaryById(String sellerOrderId);
    SellerOrderResponse updateStatus(String sellerOrderId, SellerOrderStatus newStatus);
}
