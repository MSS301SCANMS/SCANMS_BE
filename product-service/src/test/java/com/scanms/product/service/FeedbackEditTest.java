package com.scanms.product.service;

import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.dto.request.UpdateFeedbackRequest;
import com.scanms.product.entity.Feedback;
import com.scanms.product.entity.Product;
import com.scanms.product.entity.Store;
import com.scanms.product.exception.AppException;
import com.scanms.product.mapper.FeedbackMapper;
import com.scanms.product.repository.FeedbackRepository;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.impl.FeedbackServiceImpl;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class FeedbackEditTest {
    private final FeedbackRepository feedbacks = mock(FeedbackRepository.class);
    private final ProductRepository products = mock(ProductRepository.class);
    private final StoreRepository stores = mock(StoreRepository.class);
    private final ProductAccess access = mock(ProductAccess.class);
    private final FeedbackServiceImpl service = new FeedbackServiceImpl(feedbacks, new FeedbackMapper(), access, products, stores);
    private final Feedback review = Feedback.builder().feedbackId("review-1").customerId("buyer-1")
            .productId("product-1").orderItemId("item-1").rating(4).status(FeedbackStatus.ACTIVE).build();
    private final Product product = Product.builder().productId("product-1").storeId("store-1").build();
    private final Store store = Store.builder().storeId("store-1").build();

    @Test void reviewerCanEditAndRemoveFeedbackWithRatingRecalculated() {
        when(access.userId()).thenReturn("buyer-1");
        when(feedbacks.findById("review-1")).thenReturn(Optional.of(review));
        when(products.findById("product-1")).thenReturn(Optional.of(product));
        when(products.findByStoreId("store-1")).thenReturn(List.of(product));
        when(stores.findById("store-1")).thenReturn(Optional.of(store));
        when(feedbacks.findByProductIdInAndStatus(any(), any())).thenAnswer(call ->
                review.getStatus() == FeedbackStatus.ACTIVE ? List.of(review) : List.of());

        var updated = service.updateOwn("review-1", new UpdateFeedbackRequest(5, "Updated", List.of("image-1")));
        assertEquals(5, updated.rating());
        assertEquals("Updated", updated.comment());
        assertEquals(1, store.getRatingCount());
        assertEquals(0, BigDecimal.valueOf(5).compareTo(store.getRatingAverage()));

        var removed = service.removeOwn("review-1");
        assertEquals(FeedbackStatus.REMOVED, removed.status());
        assertEquals(0, store.getRatingCount());
        assertEquals(0, BigDecimal.ZERO.compareTo(store.getRatingAverage()));
        assertThrows(AppException.class, () -> service.updateOwn("review-1", new UpdateFeedbackRequest(1, null, null)));
    }

    @Test void anotherCustomerCannotEditFeedback() {
        when(access.userId()).thenReturn("another-buyer");
        when(feedbacks.findById("review-1")).thenReturn(Optional.of(review));

        assertThrows(AppException.class, () -> service.updateOwn("review-1", new UpdateFeedbackRequest(1, null, null)));
        verify(feedbacks, never()).save(any());
    }

    @Test void suspendedStoreOwnerCanReadHiddenFeedback() {
        review.setStatus(FeedbackStatus.HIDDEN);
        when(access.userId()).thenReturn("shop-owner");
        when(feedbacks.findById("review-1")).thenReturn(Optional.of(review));
        when(products.findById("product-1")).thenReturn(Optional.of(product));

        assertEquals(FeedbackStatus.HIDDEN, service.getById("review-1").status());
        verify(access).requireOwnedStore("store-1");
        verify(access, never()).requireApprovedStore(any());
    }

    @Test void managerCanListHiddenFeedbackForModeration() {
        review.setStatus(FeedbackStatus.HIDDEN);
        when(access.isManager()).thenReturn(true);
        when(feedbacks.findAll()).thenReturn(List.of(review));

        assertEquals(1, service.findAll().size());
        verify(access).userId();
    }
}
