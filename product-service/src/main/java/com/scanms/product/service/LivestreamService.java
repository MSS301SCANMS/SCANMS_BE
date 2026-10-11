package com.scanms.product.service;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import java.util.List;

public interface LivestreamService {
    LivestreamResponse create(CreateLivestreamRequest request);
    LivestreamResponse getById(String id);
    List<LivestreamResponse> findAll();
    LivestreamResponse update(String id, com.scanms.product.dto.request.UpdateLivestreamRequest request);
    LivestreamResponse transition(String id, com.scanms.product.constant.LivestreamStatus next);
    LivestreamResponse updateStats(String id, long viewers, long uniqueViewers, long concurrentViewers);
}
