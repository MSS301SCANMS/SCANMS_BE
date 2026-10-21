package com.scanms.product.service;

import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.dto.response.StoreApplicationResponse;
import java.util.*;

public interface StoreApplicationService {
    StoreApplicationResponse create(CreateStoreApplicationRequest request);
    StoreApplicationResponse updatePending(String id, CreateStoreApplicationRequest request);
    StoreApplicationResponse getById(String id);
    List<StoreApplicationResponse> findAll();
    StoreApplicationResponse review(String id, boolean approve, String note);
}

