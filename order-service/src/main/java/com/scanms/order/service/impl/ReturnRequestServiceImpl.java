package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.ReturnRequestMapper;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.service.ReturnRequestService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ReturnRequestServiceImpl implements ReturnRequestService {
    private final ReturnRequestRepository repository;
    private final ReturnRequestMapper mapper;

    public ReturnRequestResponse create(CreateReturnRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ReturnRequestResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ReturnRequest not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReturnRequestResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
