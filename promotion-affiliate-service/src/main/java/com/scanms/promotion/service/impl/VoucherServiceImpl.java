package com.scanms.promotion.service.impl;

import com.scanms.promotion.dto.request.CreateVoucherRequest;
import com.scanms.promotion.dto.response.VoucherResponse;
import com.scanms.promotion.exception.AppException;
import com.scanms.promotion.exception.ErrorCode;
import com.scanms.promotion.mapper.VoucherMapper;
import com.scanms.promotion.repository.VoucherRepository;
import com.scanms.promotion.service.VoucherService;
import com.scanms.promotion.service.VoucherAccess;
import com.scanms.promotion.constant.*;
import java.math.BigDecimal;
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
    private final VoucherAccess access;

    public VoucherResponse create(CreateVoucherRequest request) {
        if (repository.existsByCodeIgnoreCase(request.code().trim()))
            throw new AppException(ErrorCode.CONFLICT, "Voucher code already exists");
        if (request.issuerType() == null || request.scopeType() == null || request.discountType() == null
                || request.value() == null || request.value().signum() <= 0
                || request.validFrom() == null || request.validUntil() == null
                || !request.validFrom().isBefore(request.validUntil())
                || request.minimumSubtotal() != null && request.minimumSubtotal() < 0
                || request.cap() != null && request.cap() <= 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid voucher rules");
        if (request.discountType() == VoucherDiscountType.PERCENTAGE
                && request.value().compareTo(BigDecimal.valueOf(100)) > 0)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Percentage must not exceed 100");
        if (request.discountType() == VoucherDiscountType.FIXED) {
            try { request.value().longValueExact(); }
            catch (ArithmeticException ex) {
                throw new AppException(ErrorCode.INVALID_REQUEST, "Fixed discount must be a whole VND amount");
            }
        }
        if (request.issuerType() == VoucherIssuerType.PLATFORM) access.requireManager();
        else {
            if (request.storeId() == null || request.storeId().isBlank())
                throw new AppException(ErrorCode.INVALID_REQUEST, "Store voucher requires storeId");
            access.requireStoreOwner(request.storeId());
        }
        switch (request.scopeType()) {
            case PLATFORM -> {
                if (request.issuerType() != VoucherIssuerType.PLATFORM) throw new AppException(ErrorCode.FORBIDDEN, "Platform scope requires platform issuer");
                if (request.storeId() != null || request.productId() != null || request.livestreamId() != null)
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Platform voucher cannot reference a store or product");
            }
            case STORE -> {
                if (request.storeId() == null || request.productId() != null || request.livestreamId() != null)
                    throw new AppException(ErrorCode.INVALID_REQUEST, "Store scope requires only storeId");
                access.requireApprovedStore(request.storeId());
            }
            case PRODUCT -> {
                if (request.storeId() == null || request.productId() == null || request.livestreamId() != null) throw new AppException(ErrorCode.INVALID_REQUEST, "Product scope requires store and product");
                access.requireApprovedStore(request.storeId());
                access.requireProductStore(request.productId(), request.storeId());
            }
            case LIVESTREAM -> {
                if (request.storeId() == null || request.livestreamId() == null || request.productId() != null) throw new AppException(ErrorCode.INVALID_REQUEST, "Livestream scope requires store and livestream");
                access.requireApprovedStore(request.storeId());
                access.requireLivestreamStore(request.livestreamId(), request.storeId());
            }
        }
        if (request.limits() != null) request.limits().forEach((key, value) -> {
            if (!Set.of("totalUsageLimit", "perCustomerLimit").contains(key)
                    || !validPositiveLimit(value))
                throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid voucher usage limit");
        });
        String fundedBy = request.funding() == null ? null : Objects.toString(request.funding().get("fundedBy"), null);
        if (fundedBy != null && !Set.of("PLATFORM", "STORE", "SHARED").contains(fundedBy))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Invalid voucher funding source");
        if (request.issuerType() == VoucherIssuerType.STORE && fundedBy != null && !"STORE".equals(fundedBy))
            throw new AppException(ErrorCode.FORBIDDEN, "A store cannot create a platform-funded voucher");
        if ("SHARED".equals(fundedBy)) {
            Object rate = request.funding().get("platformRatePercent");
            if (!(rate instanceof Number number) || !validRate(number))
                throw new AppException(ErrorCode.INVALID_REQUEST, "Shared funding rate is invalid");
        }
        var voucher = mapper.toEntity(request);
        voucher.setCode(request.code().trim().toUpperCase(Locale.ROOT));
        return mapper.toResponse(repository.save(voucher));
    }

    private boolean validPositiveLimit(Object value) {
        if (!(value instanceof Number)) return false;
        try { return new BigDecimal(value.toString()).longValueExact() > 0; }
        catch (NumberFormatException | ArithmeticException ex) { return false; }
    }

    private boolean validRate(Number value) {
        try {
            BigDecimal rate = new BigDecimal(value.toString());
            return rate.signum() >= 0 && rate.compareTo(BigDecimal.valueOf(100)) <= 0;
        } catch (NumberFormatException ex) { return false; }
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
