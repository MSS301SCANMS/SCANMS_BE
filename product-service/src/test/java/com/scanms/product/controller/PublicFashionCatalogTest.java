package com.scanms.product.controller;

import com.scanms.product.constant.*;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import com.scanms.product.service.PublicCatalogService;
import com.scanms.product.service.impl.PublicCatalogServiceImpl;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicFashionCatalogTest {
    private final ProductRepository products = mock(ProductRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final ProductVariantRepository variants = mock(ProductVariantRepository.class);
    private final ProductImageRepository images = mock(ProductImageRepository.class);
    private final PublicCatalogService catalog = new PublicCatalogServiceImpl(products, stores, variants, images);
    private final PublicProductController controller = new PublicProductController(products, stores, catalog);
    private final Store shop = Store.builder().storeId("shop").name("Mộc Basics").approvalStatus(StoreStatus.APPROVED).build();
    private final Product product = Product.builder().productId("product").storeId("shop").name("Sơ mi")
            .priceVnd(299000L).status(ProductStatus.ACTIVE).sizesStock(Map.of("M", 999))
            .imageRefs(Map.of("main", "legacy.jpg")).build();

    @BeforeEach void setup() {
        when(products.findAll()).thenReturn(List.of(product));
        when(stores.findAll()).thenReturn(List.of(shop));
        when(products.findById("product")).thenReturn(Optional.of(product));
        when(stores.findById("shop")).thenReturn(Optional.of(shop));
        var active = ProductVariant.builder().variantId("variant-m").productId("product").sku("SHIRT-M")
                .color("Trắng").size("M").priceVnd(299000L).stockQuantity(7).status(ProductVariantStatus.ACTIVE).build();
        var inactive = ProductVariant.builder().variantId("variant-l").productId("product").sku("SHIRT-L")
                .color("Trắng").size("L").priceVnd(299000L).stockQuantity(100).status(ProductVariantStatus.INACTIVE).build();
        var soldOut = ProductVariant.builder().variantId("variant-s").productId("product").sku("SHIRT-S")
                .size("S").priceVnd(299000L).stockQuantity(100).status(ProductVariantStatus.OUT_OF_STOCK).build();
        when(variants.findByProductIdIn(List.of("product"))).thenReturn(List.of(active, inactive, soldOut));
        var primary = ProductImage.builder().imageId("a").productId("product").imageUrl("https://images.example/main.jpg")
                .primaryImage(true).sortOrder(10).build();
        var secondary = ProductImage.builder().imageId("b").productId("product").imageUrl("https://images.example/side.jpg")
                .primaryImage(false).sortOrder(0).build();
        when(images.findByProductIdIn(List.of("product"))).thenReturn(List.of(secondary, primary));
        when(images.findByProductId("product")).thenReturn(List.of(secondary, primary));
    }

    @Test void listingUsesRealVariantStockAndCanonicalPrimaryImage() {
        var row = catalog.visibleProducts().getFirst();
        assertEquals(7L, row.get("stockQuantity"));
        assertEquals("https://images.example/main.jpg", row.get("imageUrl"));
        assertEquals(3, ((List<?>) row.get("variants")).size());
        assertEquals(true, row.get("canPurchase"));
        verify(variants, times(1)).findByProductIdIn(List.of("product"));
        verify(images, times(1)).findByProductIdIn(List.of("product"));
    }

    @Test void shopAllCategoryAndPaginationKeepProductsVisible() {
        var payload = controller.listProducts("shop", null, "all", false, "newest", 1, 16).result();
        assertEquals(1, ((List<?>) payload.get("items")).size());
        assertEquals(1, ((Map<?, ?>) payload.get("pagination")).get("totalPages"));
        var shopController = new PublicStoreController(stores, catalog);
        var storefront = shopController.publicById("shop").result();
        assertEquals(1, storefront.get("productCount"));
        assertEquals("Mộc Basics", storefront.get("name"));
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> shopController.publicById("missing"));
    }

    @Test void landingAndLiveStockMatchTheFrontendContract() {
        var landing = controller.landing("product").result();
        assertEquals("product", ((Map<?, ?>) landing.get("product")).get("id"));
        assertEquals("shop", ((Map<?, ?>) landing.get("store")).get("id"));
        assertEquals(List.of("https://images.example/main.jpg", "https://images.example/side.jpg"), landing.get("images"));
        assertEquals(7L, ((Map<?, ?>) landing.get("availability")).get("stockQuantity"));
        var live = controller.stock("product").result();
        assertEquals(7L, live.get("stockQuantity"));
        assertEquals(3, ((List<?>) live.get("variants")).size());
        when(variants.findByProductIdIn(List.of("product"))).thenReturn(List.of());
        // Legacy stock remains supported only when no canonical variants exist.
        assertEquals(999L, controller.stock("product").result().get("stockQuantity"));
    }

    @Test void unapprovedStoresCannotBePurchasedAndAreNotListedAsApproved() {
        shop.setApprovalStatus(StoreStatus.SUSPENDED);
        assertEquals(false, catalog.productDetails(product, shop).get("canPurchase"));
        assertTrue(catalog.approvedStores().isEmpty());
    }
}
