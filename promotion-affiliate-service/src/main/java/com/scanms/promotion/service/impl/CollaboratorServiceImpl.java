package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.entity.CollaboratorProfile;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.CollaboratorProfileMapper;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.service.CollaboratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class CollaboratorServiceImpl implements CollaboratorService {

    private final CollaboratorProfileRepository repository;
    private final CollaboratorProfileMapper mapper;

    public CollaboratorProfileResponse create(ApplyCollaboratorRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public CollaboratorProfileResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "CollaboratorProfile not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<CollaboratorProfileResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    /**
     * Duyệt KOL — chỉ cho phép từ trạng thái PENDING.
     * Sau khi approve, ghi lại joinedAt = thời điểm duyệt.
     */
    @Override
    public CollaboratorProfileResponse approve(String id) {
        CollaboratorProfile profile = findOrThrow(id);

        // Idempotent: đã APPROVED rồi thì trả về luôn
        if (profile.getApprovalStatus() == CollaboratorStatus.APPROVED) {
            return mapper.toResponse(profile);
        }
        // Không cho approve KOL đã bị SUSPENDED hoặc REJECTED
        if (profile.getApprovalStatus() == CollaboratorStatus.SUSPENDED
                || profile.getApprovalStatus() == CollaboratorStatus.REJECTED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Cannot approve a collaborator with status: " + profile.getApprovalStatus());
        }

        profile.setApprovalStatus(CollaboratorStatus.APPROVED);
        profile.setJoinedAt(LocalDateTime.now());
        return mapper.toResponse(repository.save(profile));
    }

    /**
     * Từ chối KOL — có thể reject từ PENDING.
     * Idempotent nếu đã REJECTED.
     */
    @Override
    public CollaboratorProfileResponse reject(String id, String reason) {
        CollaboratorProfile profile = findOrThrow(id);

        // Idempotent
        if (profile.getApprovalStatus() == CollaboratorStatus.REJECTED) {
            return mapper.toResponse(profile);
        }
        if (profile.getApprovalStatus() == CollaboratorStatus.APPROVED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Use suspend instead of reject for an already-approved collaborator");
        }

        profile.setApprovalStatus(CollaboratorStatus.REJECTED);
        return mapper.toResponse(repository.save(profile));
    }

    private CollaboratorProfile findOrThrow(String id) {
        return repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "CollaboratorProfile not found: " + id));
    }
}
