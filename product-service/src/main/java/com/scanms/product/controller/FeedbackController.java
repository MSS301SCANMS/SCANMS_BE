package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateFeedbackRequest;
import com.scanms.product.dto.response.FeedbackResponse;
import com.scanms.product.service.FeedbackService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;
import com.scanms.product.constant.FeedbackStatus;
import com.scanms.product.dto.request.UpdateFeedbackRequest;

@RestController
@RequestMapping("/api/v1/feedbacks")
@RequiredArgsConstructor
public class FeedbackController {
    private final FeedbackService service;

    @PostMapping
    public ResponseEntity<ApiResponse<FeedbackResponse>> create(@Valid @RequestBody CreateFeedbackRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<FeedbackResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping("/mine")
    ApiResponse<List<FeedbackResponse>> mine(@RequestParam List<String> orderItemIds) {
        return ApiResponse.success(service.findMine(orderItemIds));
    }

    @GetMapping
    public ApiResponse<List<FeedbackResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PatchMapping("/{id}")
    public ApiResponse<FeedbackResponse> updateOwn(@PathVariable String id,
            @Valid @RequestBody UpdateFeedbackRequest request) {
        return ApiResponse.success(service.updateOwn(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<FeedbackResponse> removeOwn(@PathVariable String id) {
        return ApiResponse.success(service.removeOwn(id));
    }

    @PostMapping("/{id}/hide")
    public ApiResponse<FeedbackResponse> hide(@PathVariable String id) {
        return ApiResponse.success(service.moderate(id, FeedbackStatus.HIDDEN));
    }

    @PostMapping("/{id}/remove")
    public ApiResponse<FeedbackResponse> remove(@PathVariable String id) {
        return ApiResponse.success(service.moderate(id, FeedbackStatus.REMOVED));
    }
}

