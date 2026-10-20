package com.scanms.product.controller;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Public catalog endpoints consumed by SCANMS_FE marketplace / chat / storefront.
 * No authentication required.
 */
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicProductController {

    private final ProductRepository products;
    private final StoreRepository stores;

    @GetMapping("/products")
    ApiResponse<Map<String, Object>> listProducts(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "48") int limit) {

        List<Product> source = storeId != null && !storeId.isBlank()
                ? products.findByStoreId(storeId)
                : products.findAll();

        Map<String, Store> storeById = stores.findAll().stream()
                .collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));

        List<Map<String, Object>> items = source.stream()
                .filter(p -> p.getStatus() == null || p.getStatus() == ProductStatus.ACTIVE
                        || p.getStatus() == ProductStatus.OUT_OF_STOCK)
                .filter(p -> search == null || search.isBlank()
                        || containsIgnoreCase(p.getName(), search)
                        || containsIgnoreCase(p.getDescription(), search))
                .filter(p -> category == null || category.isBlank()
                        || category.equalsIgnoreCase(p.getCategory())
                        || category.equalsIgnoreCase(p.getCategoryId()))
                .sorted(productComparator(sortBy))
                .map(p -> toPublicProduct(p, storeById.get(p.getStoreId())))
                .toList();

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
        List<Map<String, Object>> categories = products.findAll().stream()
                .map(Product::getCategory)
                .filter(Objects::nonNull)
                .filter(c -> !c.isBlank())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .map(name -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("id", name);
                    row.put("name", name);
                    return row;
                })
                .toList();
        return ApiResponse.success(categories);
    }

    @GetMapping("/products/stores")
    ApiResponse<List<Map<String, Object>>> listProductStores() {
        return ApiResponse.success(publicStores());
    }

    @GetMapping("/products/{id}")
    ApiResponse<Map<String, Object>> getProduct(@PathVariable String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Product not found"));
        Store store = product.getStoreId() != null ? stores.findById(product.getStoreId()).orElse(null) : null;
        return ApiResponse.success(toPublicProduct(product, store));
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

    private List<Map<String, Object>> publicStores() {
        return stores.findAll().stream()
                .filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED)
                .map(this::toPublicStore)
                .toList();
    }

    private Map<String, Object> toPublicProduct(Product p, Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getProductId());
        row.put("productId", p.getProductId());
        row.put("storeId", p.getStoreId());
        row.put("title", p.getName());
        row.put("name", p.getName());
        row.put("description", p.getDescription());
        row.put("categoryName", p.getCategory());
        row.put("category", p.getCategory());
        row.put("price", p.getPriceVnd() != null ? p.getPriceVnd() : 0);
        row.put("priceVnd", p.getPriceVnd() != null ? p.getPriceVnd() : 0);
        row.put("status", p.getStatus() != null ? p.getStatus().name() : null);
        row.put("imageUrl", firstImage(p.getImageRefs()));
        row.put("imageRefs", p.getImageRefs());
        row.put("sizesStock", p.getSizesStock());
        row.put("attributes", p.getAttributes());
        row.put("createdAt", p.getCreatedAt());
        if (store != null) {
            Map<String, Object> storeInfo = new LinkedHashMap<>();
            storeInfo.put("id", store.getStoreId());
            storeInfo.put("name", store.getName());
            storeInfo.put("logoUrl", store.getLogoRef());
            row.put("store", storeInfo);
        }
        return row;
    }

    private Map<String, Object> toPublicStore(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getStoreId());
        row.put("storeId", store.getStoreId());
        row.put("name", store.getName());
        row.put("description", store.getDescription());
        row.put("logoUrl", store.getLogoRef());
        row.put("approvalStatus", store.getApprovalStatus());
        return row;
    }

    private static String firstImage(Map<String, Object> imageRefs) {
        if (imageRefs == null || imageRefs.isEmpty()) return null;
        Object main = imageRefs.get("main");
        if (main != null && !String.valueOf(main).isBlank()) return String.valueOf(main);
        Object first = imageRefs.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
        return first != null ? String.valueOf(first) : null;
    }

    private static boolean containsIgnoreCase(String haystack, String needle) {
        return haystack != null && needle != null && haystack.toLowerCase().contains(needle.toLowerCase());
    }

    private static Comparator<Product> productComparator(String sortBy) {
        if ("price_asc".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing(p -> p.getPriceVnd() == null ? 0L : p.getPriceVnd());
        }
        if ("price_desc".equalsIgnoreCase(sortBy)) {
            return Comparator.comparing((Product p) -> p.getPriceVnd() == null ? 0L : p.getPriceVnd()).reversed();
        }
        return Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()));
    }
}
