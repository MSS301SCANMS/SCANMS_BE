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
            throw new AppException(ErrorCode.CONFLICT, "Bạn đã có hồ sơ gian hàng đang chờ xét duyệt.");
        if (!stores.findByOwnerUserId(userId).isEmpty())
            throw new AppException(ErrorCode.CONFLICT, "Tài khoản của bạn đã có gian hàng.");
        validateOnboardingData(request.onboardingData());
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

    public StoreApplicationResponse updatePending(String id, CreateStoreApplicationRequest request) {
        String userId = access.userId();
        var application = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy hồ sơ gian hàng."));
        if (!userId.equals(application.getApplicantUserId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Bạn không thể sửa hồ sơ của tài khoản khác.");
        if (application.getStatus() != StoreApplicationStatus.PENDING)
            throw new AppException(ErrorCode.CONFLICT, "Hồ sơ đã được xử lý. Vui lòng tải lại trạng thái.");
        validateOnboardingData(request.onboardingData());
        application.setProposedStoreName(request.proposedStoreName().trim());
        application.setDescription(request.description());
        application.setOnboardingData(request.onboardingData());
        return mapper.toResponse(repository.saveAndFlush(application));
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
        var application = repository.lockById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "StoreApplication not found: " + id));
        if (application.getStatus() != StoreApplicationStatus.PENDING)
            throw new AppException(ErrorCode.CONFLICT, "Application has already been reviewed");
        if (approve) validateOnboardingData(application.getOnboardingData());
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

    private void validateOnboardingData(Map<String, Object> details) {
        if (details == null || details.isEmpty())
            throw new AppException(ErrorCode.INVALID_REQUEST, "Hồ sơ chưa có thông tin chi tiết và giấy tờ. Vui lòng gửi đầy đủ hồ sơ.");
        if (details.size() > 20 || details.values().stream()
                .anyMatch(value -> !(value instanceof String text) || text.length() > 4096))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thông tin hồ sơ gian hàng không hợp lệ.");
        var requiredFields = Map.ofEntries(
                Map.entry("businessType", "Loại hình kinh doanh"),
                Map.entry("taxCode", "Mã số thuế"),
                Map.entry("idCardNumber", "Số CCCD"),
                Map.entry("frontCardUrl", "CCCD mặt trước"),
                Map.entry("backCardUrl", "CCCD mặt sau"),
                Map.entry("businessLicenseUrl", "Giấy phép kinh doanh"),
                Map.entry("warehouseAddress", "Địa chỉ kho"),
                Map.entry("contactPhone", "Điện thoại liên hệ"),
                Map.entry("contactEmail", "Email đối soát"),
                Map.entry("bankName", "Ngân hàng"),
                Map.entry("bankAccountNumber", "Số tài khoản"),
                Map.entry("bankAccountName", "Tên chủ tài khoản"));
        var missing = requiredFields.entrySet().stream()
                .filter(field -> !(details.get(field.getKey()) instanceof String text) || text.isBlank())
                .map(Map.Entry::getValue).sorted().toList();
        if (!missing.isEmpty())
            throw new AppException(ErrorCode.INVALID_REQUEST, "Hồ sơ thiếu thông tin bắt buộc: " + String.join(", ", missing));
        if (!Set.of("INDIVIDUAL", "HOUSEHOLD", "ENTERPRISE").contains(details.get("businessType")))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Loại hình kinh doanh không hợp lệ.");
    }
}

