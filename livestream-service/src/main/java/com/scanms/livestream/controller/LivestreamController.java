package com.scanms.livestream.controller;

import com.scanms.livestream.dto.ApiResponse;
import com.scanms.livestream.dto.request.CreateLivestreamRequest;
import com.scanms.livestream.dto.response.LivestreamResponse;
import com.scanms.livestream.service.LivestreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

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
