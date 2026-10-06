package com.scanms.order.service;

import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import java.util.*;

import com.scanms.order.constant.ShipmentStatus;

public interface ShipmentService {
    ShipmentResponse create(CreateShipmentRequest request);
    ShipmentResponse getById(String id);
    List<ShipmentResponse> findAll();
    ShipmentResponse updateShipmentStatus(String shipmentId, ShipmentStatus status, String carrier, String trackingCode, String failureReason);
    ShipmentResponse getBySellerOrderId(String sellerOrderId);
}

