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
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class ReferralLinkServiceImpl implements ReferralLinkService {

    private final ReferralLinkRepository repository;
    private final ReferralLinkMapper mapper;
    private final CollaboratorProfileRepository collaboratorProfileRepository;

    /**
     * Tạo ReferralLink — chỉ cho phép khi CollaboratorProfile đã được APPROVED.
     * KOL PENDING / REJECTED / SUSPENDED không được tạo link mới.
     */
    public ReferralLinkResponse create(CreateReferralLinkRequest request) {
        CollaboratorProfile profile = collaboratorProfileRepository
                .findById(request.collaboratorId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND,
                        "CollaboratorProfile not found: " + request.collaboratorId()));

        if (profile.getApprovalStatus() != CollaboratorStatus.APPROVED) {
            throw new AppException(ErrorCode.CONFLICT,
                    "Only APPROVED collaborators can create referral links. Current status: "
                            + profile.getApprovalStatus());
        }

        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ReferralLinkResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ReferralLink not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReferralLinkResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
