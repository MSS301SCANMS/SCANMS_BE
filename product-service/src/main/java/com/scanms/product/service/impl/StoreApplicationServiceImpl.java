package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.StoreApplicationMapper;
import com.scanms.product.repository.StoreApplicationRepository;
import com.scanms.product.service.StoreApplicationService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.service.ShopRoleGranter;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.entity.Store;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.constant.StoreApplicationStatus;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreApplicationServiceImpl implements StoreApplicationService {
    private final StoreApplicationRepository repository;
    private final StoreApplicationMapper mapper;
    private final ProductAccess access;
    private final StoreRepository stores;
    private final ShopRoleGranter roleGranter;

    public StoreApplicationResponse create(CreateStoreApplicationRequest request) {
        String userId = access.userId();
        if (repository.existsByApplicantUserIdAndStatus(userId, StoreApplicationStatus.PENDING))
            throw new AppException(ErrorCode.CONFLICT, "A pending store application already exists");
        if (!stores.findByOwnerUserId(userId).isEmpty())
            throw new AppException(ErrorCode.CONFLICT, "The applicant already owns a store");
        var application = mapper.toEntity(request);
        application.setApplicantUserId(userId);
        application.setStatus(StoreApplicationStatus.PENDING);
        application.setReviewedByUserId(null);
        application.setReviewNote(null);
        application.setSubmittedAt(Instant.now());
        application.setReviewedAt(null);
        return mapper.toResponse(repository.saveAndFlush(application));
    }

    @Transactional(readOnly = true)
    public StoreApplicationResponse getById(String id) {
        var application = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "StoreApplication not found: " + id));
        if (!application.getApplicantUserId().equals(access.userId())) access.requireManager();
        return mapper.toResponse(application);
    }

    @Transactional(readOnly = true)
    public List<StoreApplicationResponse> findAll() {
        String currentUser = access.userId();
        return repository.findAll().stream()
                .filter(application -> access.isManager() || currentUser.equals(application.getApplicantUserId()))
                .map(mapper::toResponse).toList();
    }

    public StoreApplicationResponse review(String id, boolean approve, String note) {
        access.requireManager();
        var application = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "StoreApplication not found: " + id));
        if (application.getStatus() != StoreApplicationStatus.PENDING)
            throw new AppException(ErrorCode.CONFLICT, "Application has already been reviewed");
        if (approve && !stores.findByOwnerUserId(application.getApplicantUserId()).isEmpty())
            throw new AppException(ErrorCode.CONFLICT, "The applicant already owns a store");
        application.setStatus(approve ? StoreApplicationStatus.APPROVED : StoreApplicationStatus.REJECTED);
        application.setReviewedByUserId(access.userId());
        application.setReviewNote(note);
        application.setReviewedAt(Instant.now());
        repository.saveAndFlush(application);
        if (approve) {
            stores.saveAndFlush(Store.builder()
                    .ownerUserId(application.getApplicantUserId())
                    .name(application.getProposedStoreName())
                    .description(application.getDescription())
                    .approvalStatus(StoreStatus.APPROVED)
                    .ratingAverage(BigDecimal.ZERO)
                    .ratingCount(0L).build());
            roleGranter.grant(application.getApplicantUserId());
        }
        return mapper.toResponse(application);
    }
}

