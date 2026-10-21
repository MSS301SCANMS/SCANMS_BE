package com.scanms.product.controller;

import com.scanms.product.constant.*;
import com.scanms.product.entity.*;
import com.scanms.product.repository.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class ReferralCatalogTest {
    @Test void referralFilterOnlyIncludesActiveProductsFromApprovedStoresBeforePagination() {
        var products = mock(ProductRepository.class);
        var stores = mock(StoreRepository.class);
        var approved = Store.builder().storeId("approved").approvalStatus(StoreStatus.APPROVED).build();
        var pending = Store.builder().storeId("pending").approvalStatus(StoreStatus.PENDING).build();
        when(stores.findAll()).thenReturn(List.of(approved, pending));
        when(products.findAll()).thenReturn(List.of(
                Product.builder().productId("active").storeId("approved").status(ProductStatus.ACTIVE).build(),
                Product.builder().productId("sold-out").storeId("approved").status(ProductStatus.OUT_OF_STOCK).build(),
                Product.builder().productId("pending-store").storeId("pending").status(ProductStatus.ACTIVE).build(),
                Product.builder().productId("missing-store").storeId("missing").status(ProductStatus.ACTIVE).build()));
        var controller = new PublicProductController(products, stores);
        var filtered = controller.listProducts(null, null, null, true, "newest", 1, 1).result();
        assertEquals(1, filtered.get("total"));
        assertEquals("active", ((Map<?,?>)((List<?>)filtered.get("items")).getFirst()).get("id"));
        assertEquals(4, controller.listProducts(null, null, null, false, "newest", 1, 100).result().get("total"));
    }
}
