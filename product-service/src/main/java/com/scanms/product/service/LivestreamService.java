package com.scanms.product.service;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import java.util.List;

public interface LivestreamService {
    LivestreamResponse create(CreateLivestreamRequest request);
    LivestreamResponse getById(String id);
    List<LivestreamResponse> findAll();
}
