package com.scanms.catalog.service;

import com.scanms.catalog.dto.request.CreateLivestreamRequest;
import com.scanms.catalog.dto.response.LivestreamResponse;
import java.util.List;
import java.util.UUID;

public interface LivestreamService {
    LivestreamResponse create(CreateLivestreamRequest request);
    LivestreamResponse getById(UUID id);
    List<LivestreamResponse> findAll();
}
