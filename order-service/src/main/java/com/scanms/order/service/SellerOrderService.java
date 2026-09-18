package com.scanms.order.service;

import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import java.util.*;

public interface SellerOrderService {
    SellerOrderResponse create(CreateSellerOrderRequest request);
    SellerOrderResponse getById(UUID id);
    List<SellerOrderResponse> findAll();
}
