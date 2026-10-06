package com.scanms.product.mapper;

import com.scanms.product.constant.ProductVariantStatus;
import com.scanms.product.dto.request.CreateProductVariantRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProductVariantMapperTest {
    @Test
    void mapsCreateRequestToEntity() {
        String productId = java.util.UUID.randomUUID().toString();
        var request = new CreateProductVariantRequest(
                productId, "SKU-001", "M", "Black", 100_000L, 5, ProductVariantStatus.ACTIVE);
        var entity = new ProductVariantMapper().toEntity(request);
        assertEquals(productId, entity.getProductId());
        assertEquals("SKU-001", entity.getSku());
        assertEquals(100_000L, entity.getPriceVnd());
    }
}

