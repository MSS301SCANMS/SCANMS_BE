package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.LivestreamMapper;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.service.LivestreamService;
import java.util.List;
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
    public LivestreamResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Livestream not found: " + id));
    }

    @Override
    @Transactional(readOnly = true)
    public List<LivestreamResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
