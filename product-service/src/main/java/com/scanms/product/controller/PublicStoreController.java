package com.scanms.product.controller;

import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class PublicStoreController {

    private final StoreRepository stores;
    private final com.scanms.product.service.PublicCatalogService catalog;

    @GetMapping("/marketplace")
    ApiResponse<List<Map<String, Object>>> marketplace() {
        // Danh sách gian hàng đã duyệt lấy từ cache Redis (xoá khi Store thay đổi).
        return ApiResponse.success(catalog.approvedStores());
    }

    @GetMapping("/public/id/{shopId}")
    ApiResponse<Map<String, Object>> publicById(@PathVariable String shopId) {
        Store store = stores.findById(shopId)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Store not found"));
        return ApiResponse.success(toPublic(store));
    }

    @GetMapping("/public/id/{shopId}/follow")
    ApiResponse<Map<String, Object>> followStatus(@PathVariable String shopId) {
        // Soft stub for FE; follow feature can be wired later.
        return ApiResponse.success(Map.of("following", false, "shopId", shopId));
    }

    @PostMapping("/public/id/{shopId}/follow")
    ApiResponse<Map<String, Object>> follow(@PathVariable String shopId) {
        return ApiResponse.success(Map.of("following", true, "shopId", shopId));
    }

    @DeleteMapping("/public/id/{shopId}/follow")
    ApiResponse<Map<String, Object>> unfollow(@PathVariable String shopId) {
        return ApiResponse.success(Map.of("following", false, "shopId", shopId));
    }

    private Map<String, Object> toPublic(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getStoreId());
        row.put("storeId", store.getStoreId());
        row.put("name", store.getName());
        row.put("description", store.getDescription());
        row.put("logoUrl", store.getLogoRef());
        row.put("approvalStatus", store.getApprovalStatus());
        row.put("ratingAverage", store.getRatingAverage());
        row.put("ratingCount", store.getRatingCount());
        return row;
    }
}
