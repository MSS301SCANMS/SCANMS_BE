package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateOrderItemRequest;
import com.scanms.order.dto.response.OrderItemResponse;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.OrderItemMapper;
import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.service.OrderItemService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderItemServiceImpl implements OrderItemService {
    private final OrderItemRepository repository;
    private final OrderItemMapper mapper;

    public OrderItemResponse create(CreateOrderItemRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public OrderItemResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "OrderItem not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<OrderItemResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
