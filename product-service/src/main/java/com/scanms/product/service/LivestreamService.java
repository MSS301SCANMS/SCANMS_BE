package com.scanms.product.service;

import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import java.util.List;
import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.UpdateLivestreamRequest;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LivestreamService {
    LivestreamResponse create(CreateLivestreamRequest request);
    LivestreamResponse getById(String id);
    List<LivestreamResponse> findAll();
    LivestreamResponse update(String id, UpdateLivestreamRequest request);
    LivestreamResponse transition(String id, LivestreamStatus next);
    LivestreamResponse updateStats(String id, long viewers, long uniqueViewers, long concurrentViewers);
    LivestreamResponse respondInvite(String id, boolean accepted, Jwt jwt);
}
