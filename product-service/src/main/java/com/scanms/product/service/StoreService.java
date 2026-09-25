package com.scanms.product.service;

import com.scanms.product.dto.request.CreateStoreRequest;
import com.scanms.product.dto.response.StoreResponse;
import java.util.*;

public interface StoreService {
    StoreResponse create(CreateStoreRequest request);
    StoreResponse getById(String id);
    List<StoreResponse> findAll();
}
