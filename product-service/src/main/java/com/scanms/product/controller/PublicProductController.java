package com.scanms.product.controller;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.PublicCatalogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

/**
 * Public catalog endpoints consumed by SCANMS_FE marketplace / chat / storefront.
 * No authentication required. Danh sách lấy từ cache Redis ({@link PublicCatalogService});
 * chi tiết và tồn kho từng sản phẩm luôn đọc trực tiếp DB.
 */
@RestController
@RequestMapping("/api/v1/public")
public class PublicProductController {

    private final ProductRepository products;
    private final StoreRepository stores;
    private final PublicCatalogService catalog;

    @Autowired
    public PublicProductController(ProductRepository products, StoreRepository stores, PublicCatalogService catalog) {
        this.products = products;
        this.stores = stores;
        this.catalog = catalog;
    }

    public PublicProductController(ProductRepository products, StoreRepository stores) {
        this(products, stores, null);
    }

    @GetMapping("/products")
    public ApiResponse<Map<String, Object>> listProducts(
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "false") boolean eligibleForReferral,
            @RequestParam(required = false, defaultValue = "newest") String sortBy,
            @RequestParam(required = false, defaultValue = "1") int page,
            @RequestParam(required = false, defaultValue = "48") int limit) {

        Stream<Map<String, Object>> stream;
        if (catalog != null) {
            stream = catalog.visibleProducts().stream();
        } else {
            Map<String, Store> storeById = stores.findAll().stream()
                    .collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));
            List<Product> source = storeId != null && !storeId.isBlank()
                    ? products.findByStoreId(storeId)
                    : products.findAll();
            stream = source.stream()
                    .filter(p -> p.getStatus() == null || p.getStatus() == ProductStatus.ACTIVE
                            || p.getStatus() == ProductStatus.OUT_OF_STOCK)
                    .map(p -> PublicCatalogService.toPublicProduct(p, storeById.get(p.getStoreId())));
        }

        if (storeId != null && !storeId.isBlank()) {
            stream = stream.filter(p -> storeId.equals(p.get("storeId")));
        }

        if (eligibleForReferral) {
            if (catalog != null) {
                Set<String> approvedStoreIds = catalog.approvedStores().stream()
                        .map(s -> str(s.get("storeId")))
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                stream = stream.filter(p -> "ACTIVE".equalsIgnoreCase(str(p.get("status")))
                        && p.get("storeId") != null
                        && approvedStoreIds.contains(str(p.get("storeId"))));
            } else {
                Map<String, Store> storeById = stores.findAll().stream()
                        .collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));
                stream = stream.filter(p -> "ACTIVE".equalsIgnoreCase(str(p.get("status")))
                        && p.get("storeId") != null
                        && storeById.containsKey(str(p.get("storeId")))
                        && storeById.get(str(p.get("storeId"))).getApprovalStatus() == StoreStatus.APPROVED);
            }
        }

        stream = stream
                .filter(p -> search == null || search.isBlank()
                        || containsIgnoreCase(str(p.get("name")), search)
                        || containsIgnoreCase(str(p.get("description")), search))
                .filter(p -> category == null || category.isBlank() || "all".equalsIgnoreCase(category)
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
        payload.put("total", items.size());
        payload.put("page", safePage);
        payload.put("limit", safeLimit);
        payload.put("pagination", Map.of("total", items.size(), "page", safePage,
                "limit", safeLimit, "totalPages", (items.size() + safeLimit - 1) / safeLimit));
        return ApiResponse.success(payload);
    }

    @GetMapping("/products/categories")
    public ApiResponse<List<Map<String, Object>>> listCategories() {
        return ApiResponse.success(catalog != null ? catalog.categories() : Collections.emptyList());
    }

    @GetMapping("/products/stores")
    public ApiResponse<List<Map<String, Object>>> listProductStores() {
        if (catalog == null) return ApiResponse.success(Collections.emptyList());
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
    public ApiResponse<Map<String, Object>> getProduct(@PathVariable String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"));
        Store store = product.getStoreId() != null ? stores.findById(product.getStoreId()).orElse(null) : null;
        return ApiResponse.success(catalog != null ? catalog.productDetails(product, store) : PublicCatalogService.toPublicProduct(product, store));
    }

    @GetMapping("/products/{id}/landing")
    public ApiResponse<Map<String, Object>> landing(@PathVariable String id) {
        Map<String, Object> product = getProduct(id).result();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("product", product);
        payload.put("store", product.get("store"));
        payload.put("images", product.getOrDefault("images", List.of()));
        payload.put("videos", List.of());
        // No review aggregation exists here yet; do not fabricate ratings.
        Map<String, Object> reviews = new LinkedHashMap<>();
        reviews.put("averageRating", null);
        reviews.put("totalReviews", 0);
        reviews.put("starDistribution", Map.of());
        reviews.put("items", List.of());
        payload.put("reviews", reviews);
        long stock = ((Number) product.getOrDefault("stockQuantity", 0)).longValue();
        payload.put("availability", Map.of("stockQuantity", stock, "inStock", stock > 0 && Boolean.TRUE.equals(product.get("canPurchase"))));
        payload.put("policies", Map.of(
                "returnPolicy", "Xem điều kiện đổi trả khi đặt hàng.",
                "warranty", "Theo chính sách của từng gian hàng.",
                "shipping", "Phí và thời gian vận chuyển được xác nhận khi thanh toán.",
                "genuineCommitment", "Kiểm tra thông tin sản phẩm và gian hàng trước khi đặt hàng."));
        return ApiResponse.success(payload);
    }

    @GetMapping("/products/{id}/stock")
    public ApiResponse<Map<String, Object>> stock(@PathVariable String id) {
        Product product = products.findById(id)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "Product not found"));
        Store store = product.getStoreId() == null ? null : stores.findById(product.getStoreId()).orElse(null);
        Map<String, Object> details = catalog != null ? catalog.productDetails(product, store) : Map.of();
        long stock = ((Number) details.getOrDefault("stockQuantity", 0)).longValue();
        if (catalog == null && product.getSizesStock() != null) {
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
        body.put("variants", details.getOrDefault("variants", List.of()));
        body.put("status", product.getStatus());
        return ApiResponse.success(body);
    }

    @PostMapping("/products/analytics/events")
    public ApiResponse<Map<String, Object>> analytics(@RequestBody(required = false) Map<String, Object> body) {
        // Soft no-op for FE analytics calls in local/dev.
        return ApiResponse.success(Map.of("accepted", true));
    }

    @GetMapping("/stores")
    public ApiResponse<List<Map<String, Object>>> marketplaceStores() {
        return ApiResponse.success(catalog.approvedStores());
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
