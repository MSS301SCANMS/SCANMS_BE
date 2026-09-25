package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateShipmentRequest;
import com.scanms.order.dto.response.ShipmentResponse;
import com.scanms.order.exception.*;
import com.scanms.order.mapper.ShipmentMapper;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.ShipmentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ShipmentServiceImpl implements ShipmentService {
    private final ShipmentRepository repository;
    private final ShipmentMapper mapper;
    public ShipmentResponse create(CreateShipmentRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }
    @Transactional(readOnly = true)
    public ShipmentResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Shipment not found: " + id));
    }
    @Transactional(readOnly = true)
    public List<ShipmentResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}

