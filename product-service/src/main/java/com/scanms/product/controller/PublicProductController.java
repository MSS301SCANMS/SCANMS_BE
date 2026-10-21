package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.PublicCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * Public catalog endpoints consumed by SCANMS_FE marketplace / chat / storefront.
 * No authentication required. Danh sách lấy từ cache Redis ({@link PublicCatalogService});
 * chi tiết và tồn kho từng sản phẩm luôn đọc trực tiếp DB.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductRepository products;
    private final StoreRepository stores;
    private final PublicCatalogService catalog;

    @GetMapping("/products")
    ApiResponse<Map<String, Object>> listProducts(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "48") int limit) {

        Stream<Map<String, Object>> stream = catalog.visibleProducts().stream()
                .filter(p -> storeId == null || storeId.isBlank() || storeId.equals(p.get("storeId")))
                .filter(p -> search == null || search.isBlank()
                        || containsIgnoreCase(str(p.get("name")), search)
                        || containsIgnoreCase(str(p.get("description")), search))
                .filter(p -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(str(p.get("category")))
                        || category.equalsIgnoreCase(str(p.get("categoryId"))));
        Comparator<Map<String, Object>> comparator = productComparator(sortBy);
        if (comparator != null) stream = stream.sorted(comparator);
        List<Map<String, Object>> items = stream.toList();

        int safePage = Math.max(page, 1);
        int safeLimit = Math.min(Math.max(limit, 1), 100);
        int from = Math.min((safePage - 1) * safeLimit, items.size());
        int to = Math.min(from + safeLimit, items.size());
        List<Map<String, Object>> pageItems = items.subList(from, to);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("items", pageItems);
        payload.put("data", pageItems);
        payload.put("total", items.size());
        payload.put("page", safePage);
        payload.put("limit", safeLimit);
        return ApiResponse.success(payload);
    }

    @GetMapping("/products/categories")
    ApiResponse<List<Map<String, Object>>> listCategories() {
        return ApiResponse.success(catalog.categories());
    }

    @GetMapping("/products/stores")
    ApiResponse<List<Map<String, Object>>> listProductStores() {
        List<Map<String, Object>> rows = catalog.approvedStores().stream().map(s -> {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String key : List.of("id", "storeId", "name", "description", "logoUrl", "approvalStatus")) {
                row.put(key, s.get(key));
            }
            return row;
        }).toList();
        return ApiResponse.success(rows);
    }

    @GetMapping("/products/{id}")
    ApiResponse<Map<String, Object>> getProduct(@PathVariable String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found"));
        Store store = product.getStoreId() != null ? stores.findById(product.getStoreId()).orElse(null) : null;
        return ApiResponse.success(PublicCatalogService.toPublicProduct(product, store));
    }

    @GetMapping("/products/{id}/landing")
    ApiResponse<Map<String, Object>> landing(@PathVariable String id) {
        return getProduct(id);
    }

    @GetMapping("/products/{id}/stock")
    ApiResponse<Map<String, Object>> stock(@PathVariable String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found"));
        long stock = 0;
        if (product.getSizesStock() != null) {
            stock = product.getSizesStock().values().stream()
                    .mapToLong(v -> {
                        if (v instanceof Number n) return n.longValue();
                        try { return Long.parseLong(String.valueOf(v)); } catch (Exception e) { return 0L; }
                    })
                    .sum();
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("productId", product.getProductId());
        body.put("stockQuantity", stock);
        body.put("status", product.getStatus());
        return ApiResponse.success(body);
    }

    @PostMapping("/products/analytics/events")
    ApiResponse<Map<String, Object>> analytics(@RequestBody(required = false) Map<String, Object> body) {
        // Soft no-op for FE analytics calls in local/dev.
        return ApiResponse.success(Map.of("accepted", true));
    }

    private static String str(Object value) {
        return value == null ? null : value.toString();
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static long price(Map<String, Object> p) {
        return p.get("priceVnd") instanceof Number n ? n.longValue() : 0L;
    }

    /** null = giữ thứ tự cache (mới nhất trước). */
    private static Comparator<Map<String, Object>> productComparator(String sortBy) {
        if ("price_asc".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(PublicProductController::price);
        }
        if ("price_desc".equalsIgnoreCase(sortBy)) {
            return Comparator.comparingLong(PublicProductController::price).reversed();
        }
        return null;
    }
}
