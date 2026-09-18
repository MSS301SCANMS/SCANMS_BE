package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateSellerOrderRequest;
import com.scanms.order.dto.response.SellerOrderResponse;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.SellerOrderMapper;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.service.SellerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class SellerOrderServiceImpl implements SellerOrderService {
    private final SellerOrderRepository repository;
    private final SellerOrderMapper mapper;

    public SellerOrderResponse create(CreateSellerOrderRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public SellerOrderResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "SellerOrder not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<SellerOrderResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
