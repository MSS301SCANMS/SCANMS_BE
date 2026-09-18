package com.scanms.livestream.service.impl;

import com.scanms.livestream.dto.request.CreateLivestreamRequest;
import com.scanms.livestream.dto.response.LivestreamResponse;
import com.scanms.livestream.exception.AppException;
import com.scanms.livestream.exception.ErrorCode;
import com.scanms.livestream.mapper.LivestreamMapper;
import com.scanms.livestream.repository.LivestreamRepository;
import com.scanms.livestream.service.LivestreamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class LivestreamServiceImpl implements LivestreamService {
    private final LivestreamRepository repository;
    private final LivestreamMapper mapper;

    public LivestreamResponse create(CreateLivestreamRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public LivestreamResponse getById(UUID id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<LivestreamResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
