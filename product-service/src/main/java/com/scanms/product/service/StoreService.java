package com.scanms.product.service;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import java.util.*;

public interface StoreService {
    StoreResponse create(CreateStoreRequest request);
    StoreResponse getById(String id);
    List<StoreResponse> findAll();
    com.scanms.product.dto.response.StoreDashboardResponse dashboard(String storeId);
    StoreResponse setSuspended(String storeId, boolean suspended);
    StoreResponse update(String storeId, com.scanms.product.dto.request.UpdateStoreRequest request);
}
