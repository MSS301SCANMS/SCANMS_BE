package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.FeedbackMapper;
import com.scanms.product.repository.FeedbackRepository;
import com.scanms.product.service.FeedbackService;
import com.scanms.product.constant.FeedbackStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedbackServiceImpl implements FeedbackService {
    private final FeedbackRepository repository;
    private final FeedbackMapper mapper;

    public FeedbackResponse create(CreateFeedbackRequest request) {
        if (request.status() == FeedbackStatus.ACTIVE
                && repository.existsByOrderItemIdAndCustomerIdAndStatus(
                        request.orderItemId(), request.customerId(), FeedbackStatus.ACTIVE)) {
            throw new AppException(ErrorCode.CONFLICT, "An active feedback already exists for this order item");
        }
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Feedback not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
