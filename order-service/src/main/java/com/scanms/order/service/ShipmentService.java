package com.scanms.order.service;

import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import java.util.*;

public interface ShipmentService {
    ShipmentResponse create(CreateShipmentRequest request);
    ShipmentResponse getById(String id);
    List<ShipmentResponse> findAll();
}

