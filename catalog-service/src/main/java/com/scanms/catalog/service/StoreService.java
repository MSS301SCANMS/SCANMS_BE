package com.scanms.catalog.service;

import com.scanms.catalog.dto.request.CreateStoreRequest;
import com.scanms.catalog.dto.response.StoreResponse;
import java.util.*;

public interface StoreService {
    StoreResponse create(CreateStoreRequest request);
    StoreResponse getById(UUID id);
    List<StoreResponse> findAll();
}
