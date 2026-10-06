package com.scanms.order.service;

import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.OrderResponse;
import java.util.*;

import com.scanms.order.dto.request.CheckoutRequest;
import com.scanms.order.dto.response.CheckoutResponse;

public interface OrderService {
    OrderResponse create(CreateOrderRequest request);
    OrderResponse getById(String id);
    List<OrderResponse> findAll();
    CheckoutResponse checkout(CheckoutRequest request);
    OrderResponse markPaid(String orderId, String transactionReference);
    List<OrderResponse> getMyOrders(String customerId);
    CheckoutResponse getCheckoutDetails(String orderId);
    Map<String, Object> trackOrders(String phone, String orderSn);
}
