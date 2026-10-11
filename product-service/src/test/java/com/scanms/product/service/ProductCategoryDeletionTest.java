package com.scanms.product.service;

import com.scanms.product.entity.ProductCategory;
import com.scanms.product.exception.AppException;
import com.scanms.product.mapper.ProductCategoryMapper;
import com.scanms.product.repository.ProductCategoryRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.service.impl.ProductCategoryServiceImpl;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

class ProductCategoryDeletionTest {
    private final ProductCategoryRepository categories = mock(ProductCategoryRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final ProductAccess access = mock(ProductAccess.class);
    private final ProductCategoryServiceImpl service = new ProductCategoryServiceImpl(categories, products,
            new ProductCategoryMapper(), access);

    @Test void managerCanDeleteUnusedCategory() {
        var category = ProductCategory.builder().categoryId("category-1").name("Shirts").slug("shirts").build();
        when(categories.findById("category-1")).thenReturn(Optional.of(category));

        service.delete("category-1");

        verify(access).requireManager();
        verify(categories).delete(category);
    }

    @Test void usedCategoryCannotBeDeleted() {
        var category = ProductCategory.builder().categoryId("category-1").build();
        when(categories.findById("category-1")).thenReturn(Optional.of(category));
        when(products.existsByCategoryId("category-1")).thenReturn(true);

        assertThrows(AppException.class, () -> service.delete("category-1"));
        verify(categories, never()).delete(any());
    }

    @Test void parentCategoryCannotBeDeleted() {
        var category = ProductCategory.builder().categoryId("category-1").build();
        when(categories.findById("category-1")).thenReturn(Optional.of(category));
        when(categories.existsByParentId("category-1")).thenReturn(true);

        assertThrows(AppException.class, () -> service.delete("category-1"));
        verify(products, never()).existsByCategoryId(any());
        verify(categories, never()).delete(any());
    }

    @Test void nonManagerCannotDeleteCategory() {
        doThrow(new AppException(com.scanms.product.exception.ErrorCode.FORBIDDEN))
                .when(access).requireManager();

        assertThrows(AppException.class, () -> service.delete("category-1"));
        verifyNoInteractions(categories, products);
    }
}
