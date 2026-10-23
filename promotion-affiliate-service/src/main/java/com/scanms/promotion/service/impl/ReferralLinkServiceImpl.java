package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.ReferralLinkMapper;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.repository.ReferralLinkRepository;
import com.scanms.promotion.service.ReferralLinkService;
import com.scanms.promotion.service.VoucherAccess;
import com.scanms.promotion.service.ReferralProductAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import com.scanms.promotion.constant.ReferralLinkStatus;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferralLinkServiceImpl implements ReferralLinkService {

    private final ReferralLinkRepository repository;
    private final ReferralLinkMapper mapper;
    private final CollaboratorProfileRepository collaboratorProfileRepository;
    private final VoucherAccess access;
    private final ReferralProductAccess productAccess;

    /**
     * Tạo ReferralLink — chỉ cho phép khi CollaboratorProfile đã được APPROVED.
     * KOL PENDING / REJECTED / SUSPENDED không được tạo link mới.
     */
    public ReferralLinkResponse create(CreateReferralLinkRequest request) {
        CollaboratorProfile profile = collaboratorProfileRepository.lockByUserId(access.userId())
                .orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN, "Bạn chưa có hồ sơ KOL được duyệt."));
        if (request.collaboratorId() != null && !profile.getCollaboratorId().equals(request.collaboratorId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Không thể tạo liên kết thay cho KOL khác.");

        if (profile.getApprovalStatus() != CollaboratorStatus.APPROVED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Hồ sơ KOL cần được duyệt trước khi tạo liên kết tiếp thị.");
        }
        if (request.status() != null && request.status() != ReferralLinkStatus.ACTIVE)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Liên kết mới phải ở trạng thái hoạt động.");
        if (request.livestreamId() != null)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tạo liên kết sản phẩm không hỗ trợ gắn phiên phát trực tiếp.");
        String label = request.label() == null ? null : request.label().trim();
        if (label != null && (label.length() < 2 || label.length() > 150))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Tên liên kết cần từ 2 đến 150 ký tự.");
        // The browser reuses a token for retrying the identical request after an uncertain network result.
        String token = request.token() == null ? UUID.randomUUID().toString() : request.token();
        var existing = repository.findByToken(token);
        if (existing.isPresent()) {
            var link = existing.get();
            if (!profile.getCollaboratorId().equals(link.getCollaboratorId())
                    || !request.productId().equals(link.getProductId()) || !Objects.equals(label, link.getLabel())
                    || !Objects.equals(request.channel(), link.getChannel()) || !Objects.equals(request.expiresAt(), link.getExpiresAt()))
                throw new AppException(ErrorCode.CONFLICT, "Mã liên kết đã được sử dụng. Vui lòng tạo yêu cầu mới.");
            return mapper.toResponse(link);
        }
        if (request.expiresAt() != null && !request.expiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Thời hạn liên kết phải ở trong tương lai.");
        if (repository.countByCollaboratorIdAndProductId(profile.getCollaboratorId(), request.productId()) >= 20)
            throw new AppException(ErrorCode.CONFLICT, "Bạn đã đạt giới hạn 20 liên kết cho sản phẩm này.");
        productAccess.requireAvailable(request.productId());
        var entity = mapper.toEntity(request);
        entity.setCollaboratorId(profile.getCollaboratorId());
        entity.setToken(token);
        entity.setLabel(label);
        entity.setStatus(ReferralLinkStatus.ACTIVE);
        return mapper.toResponse(repository.saveAndFlush(entity));
    }

    @Transactional(readOnly = true)
    public ReferralLinkResponse getById(String id) {
        return findAll().stream().filter(link -> id.equals(link.referralLinkId())).findFirst()
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Không tìm thấy liên kết."));
    }

    @Transactional(readOnly = true)
    public List<ReferralLinkResponse> findAll() {
        String userId = access.userId();
        if (access.isManager()) return repository.findAll().stream().map(mapper::toResponse).toList();
        return collaboratorProfileRepository.findByUserId(userId)
                .map(profile -> repository.findByCollaboratorId(profile.getCollaboratorId()).stream().map(mapper::toResponse).toList())
                .orElseGet(List::of);
    }

    @Transactional(readOnly = true)
    public List<Map<String, Object>> findStoreLinks(String storeId) {
        // Authorize against the current owner before querying links created by any KOL.
        var products = access.storeProducts(storeId);
        Map<String, Map<?, ?>> byId = new HashMap<>();
        products.forEach(product -> byId.put(product.get("productId").toString(), product));
        if (byId.isEmpty()) return List.of();
        return repository.findByProductIdIn(byId.keySet()).stream().map(link -> {
            var product = byId.get(link.getProductId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("referralLinkId", link.getReferralLinkId()); row.put("collaboratorId", link.getCollaboratorId());
            row.put("storeId", storeId); row.put("productId", link.getProductId());
            row.put("productName", product.get("name")); row.put("productPrice", product.get("priceVnd"));
            if (product.get("imageRefs") instanceof Map<?, ?> images)
                row.put("productImageUrl", images.values().stream().filter(String.class::isInstance).findFirst().orElse(null));
            row.put("token", link.getToken()); row.put("status", link.getStatus());
            row.put("expiresAt", link.getExpiresAt()); row.put("createdAt", link.getCreatedAt());
            row.put("label", link.getLabel()); row.put("channel", link.getChannel());
            return row;
        }).toList();
    }
}
