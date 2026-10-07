package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CommissionPolicyScope;
import com.scanms.promotion.constant.CommissionPolicyStatus;
import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import com.scanms.promotion.dto.response.CommissionPolicyResponse;
import com.scanms.promotion.entity.CommissionPolicy;
import com.scanms.promotion.exception.*;
import com.scanms.promotion.mapper.CommissionPolicyMapper;
import com.scanms.promotion.repository.CommissionPolicyRepository;
import com.scanms.promotion.service.CommissionPolicyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CommissionPolicyServiceImpl implements CommissionPolicyService {

    private final CommissionPolicyRepository repository;
    private final CommissionPolicyMapper mapper;

    public CommissionPolicyResponse create(CreateCommissionPolicyRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CommissionPolicyResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "CommissionPolicy not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CommissionPolicyResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    /**
     * Priority resolver: COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM
     * Chỉ chọn policy đang ACTIVE và còn trong thời hạn validFrom/validTo.
     * Rate được snapshot tại thời điểm gọi — không đọc lại sau này.
     */
    @Override
    @Transactional(readOnly = true)
    public CommissionPolicy resolvePolicy(String collaboratorId, String livestreamId, String productId, String storeId) {
        Instant now = Instant.now();

        // 1. COLLABORATOR scope — policy riêng cho KOL này (ưu tiên cao nhất)
        if (collaboratorId != null) {
            Optional<CommissionPolicy> found = repository
                    .findByCollaboratorIdAndScopeTypeAndStatusOrderByPriorityDesc(
                            collaboratorId, CommissionPolicyScope.COLLABORATOR, CommissionPolicyStatus.ACTIVE)
                    .stream()
                    .filter(p -> isValid(p, now))
                    .findFirst();
            if (found.isPresent()) {
                log.debug("Resolved COLLABORATOR policy {} for collaborator {}", found.get().getPolicyId(), collaboratorId);
                return found.get();
            }
        }

        // 2. LIVESTREAM scope — policy gắn với buổi live
        if (livestreamId != null) {
            Optional<CommissionPolicy> found = repository
                    .findByLivestreamIdAndScopeTypeAndStatusOrderByPriorityDesc(
                            livestreamId, CommissionPolicyScope.LIVESTREAM, CommissionPolicyStatus.ACTIVE)
                    .stream()
                    .filter(p -> isValid(p, now))
                    .findFirst();
            if (found.isPresent()) {
                log.debug("Resolved LIVESTREAM policy {} for livestream {}", found.get().getPolicyId(), livestreamId);
                return found.get();
            }
        }

        // 3. PRODUCT scope — policy áp dụng cho sản phẩm cụ thể
        if (productId != null) {
            Optional<CommissionPolicy> found = repository
                    .findByProductIdAndScopeTypeAndStatusOrderByPriorityDesc(
                            productId, CommissionPolicyScope.PRODUCT, CommissionPolicyStatus.ACTIVE)
                    .stream()
                    .filter(p -> isValid(p, now))
                    .findFirst();
            if (found.isPresent()) {
                log.debug("Resolved PRODUCT policy {} for product {}", found.get().getPolicyId(), productId);
                return found.get();
            }
        }

        // 4. STORE scope — policy mặc định của store
        if (storeId != null) {
            Optional<CommissionPolicy> found = repository
                    .findByStoreIdAndScopeTypeAndStatusOrderByPriorityDesc(
                            storeId, CommissionPolicyScope.STORE, CommissionPolicyStatus.ACTIVE)
                    .stream()
                    .filter(p -> isValid(p, now))
                    .findFirst();
            if (found.isPresent()) {
                log.debug("Resolved STORE policy {} for store {}", found.get().getPolicyId(), storeId);
                return found.get();
            }
        }

        // 5. PLATFORM scope — fallback toàn hệ thống
        Optional<CommissionPolicy> platform = repository
                .findByScopeTypeAndStatusOrderByPriorityDesc(CommissionPolicyScope.PLATFORM, CommissionPolicyStatus.ACTIVE)
                .stream()
                .filter(p -> isValid(p, now))
                .findFirst();

        if (platform.isPresent()) {
            log.debug("Resolved PLATFORM fallback policy {}", platform.get().getPolicyId());
        } else {
            log.warn("No active commission policy found for collaborator={} product={} store={}", collaboratorId, productId, storeId);
        }
        return platform.orElse(null);
    }

    /** Policy hợp lệ khi: validFrom đã qua và validTo chưa đến (hoặc null = không hết hạn) */
    private boolean isValid(CommissionPolicy policy, Instant now) {
        return policy.getValidFrom() != null
                && policy.getValidFrom().isBefore(now)
                && (policy.getValidTo() == null || policy.getValidTo().isAfter(now));
    }
}

