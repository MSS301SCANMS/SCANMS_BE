package com.scanms.product.service;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import java.util.*;
import com.scanms.product.dto.request.UpdateStoreRequest;
import com.scanms.product.dto.response.StoreDashboardResponse;

public interface StoreService {
    StoreResponse create(CreateStoreRequest request);
    StoreResponse getById(String id);
    List<StoreResponse> findAll();
    StoreDashboardResponse dashboard(String storeId);
    StoreResponse setSuspended(String storeId, boolean suspended);
    StoreResponse update(String storeId, UpdateStoreRequest request);
}
