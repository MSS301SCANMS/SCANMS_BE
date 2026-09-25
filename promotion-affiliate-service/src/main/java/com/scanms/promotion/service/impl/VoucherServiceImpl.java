package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.dto.response.VoucherResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.VoucherMapper;
import com.scanms.promotion.repository.VoucherRepository;
import com.scanms.promotion.service.VoucherService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class VoucherServiceImpl implements VoucherService {
    private final VoucherRepository repository;
    private final VoucherMapper mapper;

    public VoucherResponse create(CreateVoucherRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public VoucherResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Voucher not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<VoucherResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }
}
