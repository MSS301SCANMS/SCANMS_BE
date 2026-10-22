package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.StoreOwnerDetailsRequest;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.ProductAccess;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.Arrays;
import java.util.Map;

/** Owner contact and warehouse addresses are separate from public store responses. */
@RestController
@RequestMapping("/api/v1/stores/{storeId}/owner-details")
@RequiredArgsConstructor
public class StoreOwnerDetailsController {
    private final ProductAccess access;
    private final StoreRepository stores;

    @GetMapping
    @Transactional(readOnly=true)
    public ApiResponse<Map<String,String>> get(@PathVariable String storeId) {
        var store=access.requireOwnedStore(storeId);
        return ApiResponse.success(store.getOwnerDetails()==null ? Map.of() : Map.copyOf(store.getOwnerDetails()));
    }

    @PutMapping
    @Transactional
    public ApiResponse<Map<String,String>> save(@PathVariable String storeId, @Valid @RequestBody StoreOwnerDetailsRequest request) {
        var store=access.requireApprovedStore(storeId);
        var details=request.toDetails();
        if (!completeAddress(details.get("warehouseAddress")) || !completeAddress(details.get("returnWarehouseAddress")))
            throw new AppException(ErrorCode.INVALID_REQUEST,"Địa chỉ kho cần số nhà/đường, phường/xã, tỉnh/thành, ngăn cách bằng dấu phẩy. Quận/huyện không bắt buộc.");
        store.setOwnerDetails(details);
        stores.saveAndFlush(store);
        return ApiResponse.success(details);
    }

    private boolean completeAddress(String address) {
        String[] parts=address.split(",",-1);
        return parts.length>=3 && Arrays.stream(parts).noneMatch(String::isBlank);
    }
}
