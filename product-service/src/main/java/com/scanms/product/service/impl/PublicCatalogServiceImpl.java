package com.scanms.product.service.impl;

import com.scanms.product.config.CacheNames;
import com.scanms.product.constant.ProductStatus;
import com.scanms.product.constant.ProductVariantStatus;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.ProductImage;
import com.scanms.product.entity.ProductVariant;
import com.scanms.product.entity.Store;
import com.scanms.product.repository.ProductImageRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.ProductVariantRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.PublicCatalogService;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Dữ liệu catalog công khai (marketplace / storefront), cache trong Redis.
 * Trước đây mỗi request /public/products đều findAll() toàn bộ sản phẩm + gian hàng; nay chỉ
 * truy vấn DB khi cache trống/hết hạn, còn lọc/sắp xếp/phân trang làm trên bản cache.
 * Cache bị xoá ngay khi Store/Product thay đổi (xem CatalogCacheInvalidator).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicCatalogServiceImpl implements PublicCatalogService {
    private final ProductRepository products;
    private final StoreRepository stores;
    private final ProductVariantRepository variants;
    private final ProductImageRepository images;

    /**
     * Sản phẩm được hiển thị công khai (ACTIVE / OUT_OF_STOCK / chưa đặt trạng thái), mới nhất trước.
     */
    @Override
    @Cacheable(CacheNames.PUBLIC_PRODUCTS)
    public List<Map<String, Object>> visibleProducts() {
        Map<String, Store> storeById = stores.findAll().stream().collect(Collectors.toMap(Store::getStoreId, s -> s, (a, b) -> a));
        List<Product> source = products.findAll();
        if (source.isEmpty())
            return new ArrayList<>();
        List<String> ids = source.stream().map(Product::getProductId).toList();
        Map<String, List<ProductVariant>> variantsByProduct = variants.findByProductIdIn(ids).stream().collect(Collectors.groupingBy(ProductVariant::getProductId));
        Map<String, List<ProductImage>> imagesByProduct = images.findByProductIdIn(ids).stream().collect(Collectors.groupingBy(ProductImage::getProductId));
        return source.stream().filter(p -> p.getStatus() == null || p.getStatus() == ProductStatus.ACTIVE || p.getStatus() == ProductStatus.OUT_OF_STOCK).sorted(Comparator.comparing(Product::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))).map(p -> enrich(p, storeById.get(p.getStoreId()), variantsByProduct.getOrDefault(p.getProductId(), List.of()), imagesByProduct.getOrDefault(p.getProductId(), List.of()))).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Read current variant stock directly; detail/stock endpoints must not serve cached inventory.
     */
    @Override
    public Map<String, Object> productDetails(Product product, Store store) {
        return enrich(product, store, variants.findByProductIdIn(List.of(product.getProductId())), images.findByProductId(product.getProductId()));
    }

    private static Map<String, Object> enrich(Product product, Store store, List<ProductVariant> productVariants, List<ProductImage> productImages) {
        Map<String, Object> row = toPublicProduct(product, store);
        List<String> gallery = productImages.stream().sorted(Comparator.comparing(ProductImage::isPrimaryImage).reversed().thenComparing(i -> i.getSortOrder() == null ? Integer.MAX_VALUE : i.getSortOrder()).thenComparing(ProductImage::getImageId, Comparator.nullsLast(Comparator.naturalOrder()))).map(ProductImage::getImageUrl).filter(Objects::nonNull).filter(url -> !url.isBlank()).distinct().toList();
        if (gallery.isEmpty() && product.getImageRefs() != null) {
            gallery = Stream.concat(Stream.of(firstImage(product.getImageRefs())), product.getImageRefs().values().stream()).filter(Objects::nonNull).map(Object::toString).filter(url -> !url.isBlank()).distinct().toList();
        }
        String mainImage = gallery.isEmpty() ? null : gallery.getFirst();
        List<Map<String, Object>> variantRows = productVariants.stream().sorted(Comparator.comparing(ProductVariant::getSku, Comparator.nullsLast(Comparator.naturalOrder()))).map(v -> {
            Map<String, Object> variant = new LinkedHashMap<>();
            boolean active = v.getStatus() == ProductVariantStatus.ACTIVE;
            variant.put("id", v.getVariantId());
            variant.put("sku", v.getSku());
            variant.put("name", Stream.of(v.getColor(), v.getSize()).filter(Objects::nonNull).filter(s -> !s.isBlank()).collect(Collectors.joining(" / ")));
            Map<String, String> attributes = new LinkedHashMap<>();
            if (v.getSize() != null) attributes.put("size", v.getSize());
            if (v.getColor() != null) attributes.put("color", v.getColor());
            variant.put("attributes", attributes);
            variant.put("size", v.getSize());
            variant.put("color", v.getColor());
            variant.put("price", v.getPriceVnd());
            variant.put("stockQuantity", active && v.getStockQuantity() != null ? Math.max(0, v.getStockQuantity()) : 0);
            variant.put("isActive", active);
            variant.put("imageUrl", mainImage);
            return variant;
        }).toList();
        long stock = variantRows.stream().mapToLong(v -> ((Number) v.get("stockQuantity")).longValue()).sum();
        if (productVariants.isEmpty() && product.getSizesStock() != null) {
            stock = product.getSizesStock().values().stream().filter(Number.class::isInstance).mapToLong(v -> Math.max(0, ((Number) v).longValue())).sum();
        }
        boolean active = product.getStatus() == ProductStatus.ACTIVE;
        row.put("sku", product.getProductId());
        row.put("images", gallery);
        row.put("imageUrl", mainImage);
        row.put("variants", variantRows);
        row.put("stockQuantity", stock);
        row.put("isActive", active);
        row.put("canPurchase", active && stock > 0 && store != null && store.getApprovalStatus() == StoreStatus.APPROVED);
        row.put("material", product.getMaterial());
        row.put("sizeGuide", product.getSizeGuide());
        return row;
    }

    /**
     * Gian hàng đã duyệt (marketplace), kèm rating.
     */
    @Override
    @Cacheable(CacheNames.PUBLIC_STORES)
    public List<Map<String, Object>> approvedStores() {
        Map<String, List<Product>> byStore = products.findAll().stream().filter(p -> p.getStoreId() != null && (p.getStatus() == ProductStatus.ACTIVE || p.getStatus() == ProductStatus.OUT_OF_STOCK)).collect(Collectors.groupingBy(Product::getStoreId));
        return stores.findAll().stream().filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED).map(s -> {
            Map<String, Object> row = toPublicStore(s);
            List<Product> catalog = byStore.getOrDefault(s.getStoreId(), List.of());
            row.put("productCount", catalog.size());
            row.put("totalProducts", catalog.size());
            row.put("categories", catalog.stream().map(Product::getCategory).filter(Objects::nonNull).distinct().sorted().toList());
            return row;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Danh mục (tên category) suy ra từ toàn bộ sản phẩm.
     */
    @Override
    @Cacheable(CacheNames.PUBLIC_CATEGORIES)
    public List<Map<String, Object>> categories() {
        return products.findAll().stream().map(Product::getCategory).filter(Objects::nonNull).filter(c -> !c.isBlank()).distinct().sorted(String.CASE_INSENSITIVE_ORDER).map(name -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", name);
            row.put("name", name);
            return row;
        }).collect(Collectors.toCollection(ArrayList::new));
    }

    public static Map<String, Object> toPublicProduct(Product p, Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", p.getProductId());
        row.put("productId", p.getProductId());
        row.put("storeId", p.getStoreId());
        row.put("title", p.getName());
        row.put("name", p.getName());
        row.put("description", p.getDescription());
        row.put("categoryId", p.getCategoryId());
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
            Map<String, Object> storeInfo = toPublicStore(store);
            row.put("store", storeInfo);
        }
        return row;
    }

    public static Map<String, Object> toPublicStore(Store store) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", store.getStoreId());
        row.put("storeId", store.getStoreId());
        row.put("slug", store.getStoreId());
        row.put("name", store.getName());
        row.put("description", store.getDescription());
        row.put("logoUrl", store.getLogoRef());
        row.put("approvalStatus", store.getApprovalStatus());
        row.put("ratingAverage", store.getRatingAverage());
        row.put("ratingCount", store.getRatingCount());
        row.put("rating", store.getRatingAverage());
        row.put("reviewCount", store.getRatingCount() == null ? 0L : store.getRatingCount());
        row.put("isVerified", store.getApprovalStatus() == StoreStatus.APPROVED);
        row.put("isActive", store.getApprovalStatus() == StoreStatus.APPROVED);
        row.put("followerCount", 0);
        return row;
    }

    private static String firstImage(Map<String, Object> imageRefs) {
        if (imageRefs == null || imageRefs.isEmpty()) return null;
        Object main = imageRefs.get("main");
        if (main != null && !String.valueOf(main).isBlank()) return String.valueOf(main);
        Object first = imageRefs.values().stream().filter(Objects::nonNull).findFirst().orElse(null);
        return first != null ? String.valueOf(first) : null;
    }
}
