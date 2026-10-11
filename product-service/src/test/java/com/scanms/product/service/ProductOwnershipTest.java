package com.scanms.product.service;

import com.scanms.product.constant.ProductStatus;
import com.scanms.product.constant.ProductVariantStatus;
import com.scanms.product.dto.request.CreateProductRequest;
import com.scanms.product.dto.request.CreateProductVariantRequest;
import com.scanms.product.entity.Product;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.ProductMapper;
import com.scanms.product.mapper.ProductVariantMapper;
import com.scanms.product.repository.ProductCategoryRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.ProductVariantRepository;
import com.scanms.product.service.impl.ProductServiceImpl;
import com.scanms.product.service.impl.ProductVariantServiceImpl;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ProductOwnershipTest {
    private final ProductAccess access = mock(ProductAccess.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final ProductServiceImpl service = new ProductServiceImpl(products, new ProductMapper(), access, categories);

    private CreateProductRequest productRequest() {
        return new CreateProductRequest("store-1", null, "Linen shirt", null, null, "linen",
                Map.of("color", "white"), Map.of("M", 999), null, Map.of("image", "legacy"), 100000L, ProductStatus.ACTIVE);
    }

    @Test void suspendedStoreCannotCreateAProduct() {
        doThrow(new AppException(ErrorCode.CONFLICT, "Store is suspended")).when(access).requireApprovedStore("store-1");
        assertThrows(AppException.class, () -> service.create(productRequest()));
        verify(products, never()).save(any());
    }

    @Test void productUsesVariantAndImageRecordsInsteadOfLegacyJsonFields() {
        when(products.save(any(Product.class))).thenAnswer(call -> call.getArgument(0));
        var created = service.create(productRequest());
        assertNull(created.sizesStock());
        assertNull(created.imageRefs());
        assertEquals(ProductStatus.ACTIVE, created.status());
        verify(access).requireApprovedStore("store-1");
    }

    @Test void variantCannotBeAddedToAnotherOwnersProduct() {
        var variants = mock(ProductVariantRepository.class);
        var variantService = new ProductVariantServiceImpl(variants, new ProductVariantMapper(), products, access);
        when(products.findById("product-1")).thenReturn(Optional.of(Product.builder().productId("product-1").storeId("store-1").build()));
        doThrow(new AppException(ErrorCode.FORBIDDEN)).when(access).requireApprovedStore("store-1");
        var request = new CreateProductVariantRequest("product-1", "SKU-1", "M", "White", 100000L, 5, ProductVariantStatus.ACTIVE);
        assertThrows(AppException.class, () -> variantService.create(request));
        verify(variants, never()).save(any());
    }
}
