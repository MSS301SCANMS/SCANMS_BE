package com.scanms.livestream.service;

import com.scanms.livestream.dto.request.CreateLivestreamRequest;
import com.scanms.livestream.dto.response.LivestreamResponse;
import java.util.*;

public interface LivestreamService {
    LivestreamResponse create(CreateLivestreamRequest request);
    LivestreamResponse getById(UUID id);
    List<LivestreamResponse> findAll();
}
