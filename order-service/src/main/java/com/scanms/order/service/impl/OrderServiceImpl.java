package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.OrderResponse;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.OrderMapper;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderServiceImpl implements OrderService {
    private final OrderRepository repository;
    private final OrderMapper mapper;

    public OrderResponse create(CreateOrderRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public OrderResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Order not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
