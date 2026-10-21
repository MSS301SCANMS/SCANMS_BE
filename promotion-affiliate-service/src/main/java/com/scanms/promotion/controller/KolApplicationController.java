package com.scanms.promotion.controller;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.service.KolApplicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;
import java.util.Map;

@RestController @RequiredArgsConstructor
public class KolApplicationController {
    private final KolApplicationService service;
    @PostMapping("/api/v1/collaborators/application")
    @PreAuthorize("@financeBoundary.canApply(authentication)")
    public ApiResponse<CollaboratorProfileResponse> submit(@RequestBody Map<String,Object> input) {
        return ApiResponse.success(service.submit(input));
    }
}
