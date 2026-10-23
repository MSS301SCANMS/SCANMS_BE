package com.scanms.promotion.controller;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import com.scanms.promotion.mapper.CollaboratorProfileMapper;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.util.Map;
import java.util.Objects;
import com.scanms.promotion.service.VoucherAccess;
@RestController @RequiredArgsConstructor
public class CollaboratorIdentityController {
    private final CollaboratorProfileRepository repository;
    private final CollaboratorProfileMapper mapper;
    private final VoucherAccess access;
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    @GetMapping("/api/v1/collaborators/me") public ApiResponse<CollaboratorProfileResponse> me(@AuthenticationPrincipal Jwt jwt) {
        String userId=access.userId();
        return ApiResponse.success(repository.findByUserId(Objects.toString(userId, null))
                .map(mapper::toResponse)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Collaborator profile not found")));
    }
}
