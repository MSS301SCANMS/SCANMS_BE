package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.dto.response.ReferralLinkResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.ReferralLinkMapper;
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

    public ReferralLinkResponse create(CreateReferralLinkRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public ReferralLinkResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "ReferralLink not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<ReferralLinkResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
