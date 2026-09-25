package com.scanms.payment.service.impl;

import com.scanms.payment.dto.request.CreatePaymentRequest;
import com.scanms.payment.dto.response.PaymentResponse;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import com.scanms.payment.mapper.PaymentMapper;
import com.scanms.payment.repository.PaymentRepository;
import com.scanms.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class PaymentServiceImpl implements PaymentService {
    private final PaymentRepository repository;
    private final PaymentMapper mapper;

    public PaymentResponse create(CreatePaymentRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Payment not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<PaymentResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
