package com.scanms.product.service.impl;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import com.scanms.product.mapper.StoreMapper;
import com.scanms.product.repository.StoreRepository;
import com.scanms.product.service.StoreService;
import com.scanms.product.service.ProductAccess;
import com.scanms.product.repository.ProductRepository;
import com.scanms.product.repository.LivestreamRepository;
import com.scanms.product.dto.response.StoreDashboardResponse;
import com.scanms.product.constant.StoreStatus;
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
import com.scanms.product.config.CacheNames;
import com.scanms.product.dto.request.UpdateStoreRequest;
import org.springframework.cache.annotation.Cacheable;

@Service
@RequiredArgsConstructor
@Transactional
public class StoreServiceImpl implements StoreService {
    private final StoreRepository repository;
    private final StoreMapper mapper;
    private final ProductAccess access;
    private final ProductRepository products;
    private final LivestreamRepository livestreams;
    @Value("${clients.order.url:http://localhost:8083}") private String orderUrl;

    public StoreResponse create(CreateStoreRequest request) {
        throw new AppException(ErrorCode.CONFLICT, "Submit a store application for manager approval");
    }

    @Transactional(readOnly = true)
    public StoreResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found: " + id));
    }

    @Transactional(readOnly = true)
    @Cacheable(CacheNames.STORES_ALL)
    public List<StoreResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public StoreDashboardResponse dashboard(String storeId) {
        var store = access.requireOwnedStore(storeId);
        long orderCount, paidRevenueVnd;
        try {
            var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
            factory.setReadTimeout(Duration.ofSeconds(10));
            Map<?, ?> response = RestClient.builder().requestFactory(factory).build().get()
                    .uri(orderUrl + "/api/v1/store-sales/" + storeId)
                    .headers(headers -> headers.setBearerAuth(access.bearerToken())).retrieve().body(Map.class);
            if (response == null || !(response.get("result") instanceof Map<?, ?> sales)
                    || !(sales.get("orderCount") instanceof Number count)
                    || !(sales.get("paidRevenueVnd") instanceof Number revenue))
                throw new AppException(ErrorCode.CONFLICT, "Order dashboard data is unavailable");
            orderCount = count.longValue();
            paidRevenueVnd = revenue.longValue();
        } catch (RestClientException exception) {
            throw new AppException(ErrorCode.CONFLICT, "Order dashboard data is unavailable");
        }
        return new StoreDashboardResponse(storeId, store.getRatingAverage(), store.getRatingCount() == null ? 0 : store.getRatingCount(),
                products.findByStoreId(storeId).size(), livestreams.countByStoreId(storeId), orderCount, paidRevenueVnd);
    }

    public StoreResponse setSuspended(String storeId, boolean suspended) {
        access.requireManager();
        var store = repository.findById(storeId)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "Store not found: " + storeId));
        if (store.getApprovalStatus() == StoreStatus.PENDING)
            throw new AppException(ErrorCode.CONFLICT, "Store has not been approved");
        store.setApprovalStatus(suspended ? StoreStatus.SUSPENDED : StoreStatus.APPROVED);
        return mapper.toResponse(repository.save(store));
    }

    public StoreResponse update(String storeId, UpdateStoreRequest request) {
        var store = access.requireApprovedStore(storeId);
        if (request.name() != null) store.setName(request.name());
        if (request.description() != null) store.setDescription(request.description());
        return mapper.toResponse(repository.save(store));
    }
}
