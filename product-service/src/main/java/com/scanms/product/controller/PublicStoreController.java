package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.PublicCatalogService;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/** Read-only storefront contract used by the marketplace and individual shop pages. */
@RestController
@RequestMapping("/api/v1/stores")
@RequiredArgsConstructor
public class PublicStoreController {
    private final StoreRepository stores;
    private final PublicCatalogService catalog;

    @GetMapping("/marketplace")
    public ApiResponse<List<Map<String, Object>>> marketplace() {
        return ApiResponse.success(catalog.approvedStores());
    }

    @GetMapping("/public/id/{shopId}")
    public ApiResponse<Map<String, Object>> publicById(@PathVariable String shopId) {
        return ApiResponse.success(catalog.approvedStores().stream()
                .filter(store -> shopId.equals(store.get("id"))).findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Shop not found")));
    }

    @GetMapping("/public/id/{shopId}/follow")
    public ApiResponse<Map<String, Object>> followStatus(@PathVariable String shopId) {
        // Existing compatibility stub; persistence is not part of the catalog seed.
        return ApiResponse.success(Map.of("following", false, "shopId", shopId));
    }

    @PostMapping("/public/id/{shopId}/follow")
    public ApiResponse<Map<String, Object>> follow(@PathVariable String shopId) {
        return ApiResponse.success(Map.of("following", true, "shopId", shopId));
    }

    @DeleteMapping("/public/id/{shopId}/follow")
    public ApiResponse<Map<String, Object>> unfollow(@PathVariable String shopId) {
        return ApiResponse.success(Map.of("following", false, "shopId", shopId));
    }
}
