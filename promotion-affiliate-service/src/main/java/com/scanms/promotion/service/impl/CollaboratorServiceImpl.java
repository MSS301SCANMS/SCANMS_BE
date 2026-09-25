package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.CollaboratorProfileMapper;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.service.CollaboratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
}
