package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import com.scanms.product.exception.*;
import com.scanms.product.mapper.FeedbackMapper;
import com.scanms.product.repository.FeedbackRepository;
import com.scanms.product.service.FeedbackService;
import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.StoreRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class FeedbackServiceImpl implements FeedbackService {
    private final FeedbackRepository repository;
    private final FeedbackMapper mapper;
    private final ProductAccess access;
    private final ProductRepository products;
    private final StoreRepository stores;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;

    public FeedbackResponse create(CreateFeedbackRequest request) {
        String customerId = access.userId();
        var product = products.findById(request.productId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        var item = orderResult("/api/v1/order-items/" + request.orderItemId());
        if (!request.productId().equals(item.get("productId")))
            throw new AppException(ErrorCode.FORBIDDEN, "Order item does not contain this product");
        var sellerOrder = orderResult("/api/v1/seller-orders/" + item.get("sellerOrderId"));
        var order = orderResult("/api/v1/orders/" + sellerOrder.get("orderId"));
        if (!product.getStoreId().equals(sellerOrder.get("storeId"))
                || !customerId.equals(order.get("customerId"))
                || !java.util.Set.of("PAID", "PROCESSING", "COMPLETED")
                        .contains(Objects.toString(order.get("status"), ""))
                || !java.util.Set.of("DELIVERED", "COMPLETED")
                        .contains(Objects.toString(sellerOrder.get("status"), "")))
            throw new AppException(ErrorCode.FORBIDDEN, "Only the buyer of a delivered item may review it");
        if (repository.existsByOrderItemIdAndCustomerId(request.orderItemId(), customerId)) {
            throw new AppException(ErrorCode.CONFLICT, "This order item has already been reviewed");
        }
        var feedback = mapper.toEntity(request);
        feedback.setCustomerId(customerId);
        feedback.setStatus(FeedbackStatus.ACTIVE);
        var saved = repository.save(feedback);
        refreshStoreRating(product.getStoreId());
        return mapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public FeedbackResponse getById(String id) {
        var feedback = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Feedback not found: " + id));
        if (feedback.getStatus() != FeedbackStatus.ACTIVE) {
            String currentUser = access.userId();
            if (!feedback.getCustomerId().equals(currentUser) && !access.isManager()) {
                var product = products.findById(feedback.getProductId())
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
                access.requireOwnedStore(product.getStoreId());
            }
        }
        return mapper.toResponse(feedback);
    }

    @Transactional(readOnly = true)
    public List<FeedbackResponse> findAll() {
        boolean manager = access.isManager();
        if (manager) access.userId();
        return repository.findAll().stream().filter(feedback -> manager || feedback.getStatus() == FeedbackStatus.ACTIVE)
                .map(mapper::toResponse).toList();
    }

    public FeedbackResponse updateOwn(String id, com.scanms.product.dto.request.UpdateFeedbackRequest request) {
        var feedback = ownedActive(id);
        if (request.rating() != null) feedback.setRating(request.rating());
        if (request.comment() != null) feedback.setComment(request.comment());
        if (request.imageRefs() != null) feedback.setImageRefs(request.imageRefs());
        repository.save(feedback);
        refreshStoreRating(storeId(feedback));
        return mapper.toResponse(feedback);
    }

    public FeedbackResponse removeOwn(String id) {
        var feedback = ownedActive(id);
        feedback.setStatus(FeedbackStatus.REMOVED);
        repository.save(feedback);
        refreshStoreRating(storeId(feedback));
        return mapper.toResponse(feedback);
    }

    private com.scanms.product.entity.Feedback ownedActive(String id) {
        var feedback = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Feedback not found"));
        if (!feedback.getCustomerId().equals(access.userId()))
            throw new AppException(ErrorCode.FORBIDDEN, "Only the reviewer can change this feedback");
        if (feedback.getStatus() != FeedbackStatus.ACTIVE)
            throw new AppException(ErrorCode.CONFLICT, "Feedback is not active");
        return feedback;
    }

    private String storeId(com.scanms.product.entity.Feedback feedback) {
        return products.findById(feedback.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"))
                .getStoreId();
    }

    public FeedbackResponse moderate(String id, FeedbackStatus status) {
        access.requireManager();
        if (status != FeedbackStatus.HIDDEN && status != FeedbackStatus.REMOVED)
            throw new AppException(ErrorCode.INVALID_REQUEST, "Only hide or remove is supported");
        var feedback = repository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Feedback not found"));
        var product = products.findById(feedback.getProductId())
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Product not found"));
        feedback.setStatus(status);
        repository.save(feedback);
        refreshStoreRating(product.getStoreId());
        return mapper.toResponse(feedback);
    }

    private void refreshStoreRating(String storeId) {
        var store = stores.findById(storeId).orElseThrow();
        var ids = products.findByStoreId(storeId).stream().map(com.scanms.product.entity.Product::getProductId).toList();
        var active = ids.isEmpty() ? java.util.List.<com.scanms.product.entity.Feedback>of()
                : repository.findByProductIdInAndStatus(ids, FeedbackStatus.ACTIVE);
        store.setRatingCount((long) active.size());
        store.setRatingAverage(active.isEmpty() ? BigDecimal.ZERO :
                BigDecimal.valueOf(active.stream().mapToLong(com.scanms.product.entity.Feedback::getRating).sum())
                        .divide(BigDecimal.valueOf(active.size()), 2, RoundingMode.HALF_UP));
    }

    private Map<?, ?> orderResult(String path) {
        try {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            factory.setReadTimeout(Duration.ofSeconds(10));
            Map<?, ?> response = RestClient.builder().requestFactory(factory).build().get()
                    .uri(orderUrl + path).headers(h -> h.setBearerAuth(access.bearerToken()))
                    .retrieve().body(Map.class);
            if (response != null && response.get("result") instanceof Map<?, ?> result) return result;
        } catch (RestClientException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Cannot verify the completed order item");
        }
        throw new AppException(ErrorCode.CONFLICT, "Cannot verify the completed order item");
    }
}
