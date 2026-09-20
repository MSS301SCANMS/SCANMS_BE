package com.scanms.catalog.service.impl;

import com.scanms.catalog.dto.request.CreateLivestreamRequest;
import com.scanms.catalog.dto.response.LivestreamResponse;
import com.scanms.catalog.exception.AppException;
import com.scanms.catalog.exception.ErrorCode;
import com.scanms.catalog.mapper.LivestreamMapper;
import com.scanms.catalog.repository.LivestreamRepository;
import com.scanms.catalog.service.LivestreamService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class LivestreamServiceImpl implements LivestreamService {
    private final LivestreamRepository repository;
    private final LivestreamMapper mapper;

    @Override
    public LivestreamResponse create(CreateLivestreamRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Override
    @Transactional(readOnly = true)
    public LivestreamResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LivestreamResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
