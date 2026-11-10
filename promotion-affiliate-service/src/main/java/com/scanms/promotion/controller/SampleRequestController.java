package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.*;
import com.scanms.promotion.entity.SampleRequest;
import com.scanms.promotion.service.SampleRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sample-requests")
@RequiredArgsConstructor
public class SampleRequestController {
    private final SampleRequestService service;

    @PostMapping
    public ApiResponse<SampleRequest> create(@Valid @RequestBody SampleCreateRequest input) {
        return ApiResponse.created(service.create(input));
    }

    @GetMapping("/my")
    public ApiResponse<List<SampleRequest>> mine() {
        return ApiResponse.success(service.list(false));
    }

    @GetMapping("/shop")
    public ApiResponse<List<SampleRequest>> shop() {
        return ApiResponse.success(service.list(true));
    }

    @GetMapping("/{id}")
    public ApiResponse<SampleRequest> get(@PathVariable String id) {
        return ApiResponse.success(service.get(id));
    }

    @PatchMapping("/{id}/{action}")
    public ApiResponse<SampleRequest> decide(@PathVariable String id, @PathVariable String action,
                                             @Valid @RequestBody SampleDecisionRequest input) {
        return ApiResponse.success(service.decide(id, action, input));
    }
}
