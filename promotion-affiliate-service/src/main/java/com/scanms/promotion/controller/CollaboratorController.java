package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.request.ApplyCollaboratorRequest;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.service.CollaboratorService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/collaborators")
@RequiredArgsConstructor
public class CollaboratorController {
    private final CollaboratorService service;

    @PostMapping
    ResponseEntity<ApiResponse<CollaboratorProfileResponse>> create(@Valid @RequestBody ApplyCollaboratorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<CollaboratorProfileResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<CollaboratorProfileResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
