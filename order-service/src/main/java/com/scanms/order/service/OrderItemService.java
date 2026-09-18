package com.scanms.order.service;

import com.scanms.order.dto.request.CreateOrderItemRequest;
import com.scanms.order.dto.response.OrderItemResponse;
import java.util.*;

public interface OrderItemService {
    OrderItemResponse create(CreateOrderItemRequest request);
    OrderItemResponse getById(UUID id);
    List<OrderItemResponse> findAll();
}
