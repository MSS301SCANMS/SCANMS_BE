package com.scanms.catalog.controller;

import com.scanms.catalog.dto.ApiResponse;
import com.scanms.catalog.dto.request.CreateLivestreamRequest;
import com.scanms.catalog.dto.response.LivestreamResponse;
import com.scanms.catalog.service.LivestreamService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/livestreams")
@RequiredArgsConstructor
public class LivestreamController {
    private final LivestreamService service;

    @PostMapping
    ResponseEntity<ApiResponse<LivestreamResponse>> create(@Valid @RequestBody CreateLivestreamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<LivestreamResponse> getById(@PathVariable UUID id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<LivestreamResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
