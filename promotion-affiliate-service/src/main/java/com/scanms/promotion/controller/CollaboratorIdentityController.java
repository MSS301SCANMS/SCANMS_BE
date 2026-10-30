package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.service.CollaboratorService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class CollaboratorIdentityController {
    private final CollaboratorService service;

    @GetMapping("/api/v1/collaborators/me")
    public ApiResponse<CollaboratorProfileResponse> me() {
        return ApiResponse.success(service.me());
    }
}
