package com.scanms.order.service;

import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.OrderResponse;
import java.util.*;

public interface OrderService {
    OrderResponse create(CreateOrderRequest request);
    OrderResponse getById(String id);
    List<OrderResponse> findAll();
}
