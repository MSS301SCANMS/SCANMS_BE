package com.scanms.product.service;

import com.scanms.product.dto.request.SearchEventRequest;
import com.scanms.product.dto.response.SearchEventResponse;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.List;

public interface SearchHistoryService {
    SearchEventResponse record(SearchEventRequest input, Jwt jwt);

    List<SearchEventResponse> recent(Jwt jwt);

    void clear(Jwt jwt);
}
