package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.CreateLivestreamRequest;
import com.scanms.product.dto.response.LivestreamResponse;
import com.scanms.product.service.LivestreamService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PatchMapping;
import com.scanms.product.constant.LivestreamStatus;
import com.scanms.product.dto.request.UpdateLivestreamRequest;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/livestreams")
@RequiredArgsConstructor
public class LivestreamController {
    private final LivestreamService service;

    @PostMapping
    public ResponseEntity<ApiResponse<LivestreamResponse>> create(@Valid @RequestBody CreateLivestreamRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<LivestreamResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    public ApiResponse<List<LivestreamResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }

    @PatchMapping("/{id}")
    public ApiResponse<LivestreamResponse> update(@PathVariable String id, @Valid @RequestBody UpdateLivestreamRequest request) {
        return ApiResponse.success(service.update(id, request));
    }

    @PostMapping("/{id}/start")
    public ApiResponse<LivestreamResponse> start(@PathVariable String id) {
        return ApiResponse.success(service.transition(id, LivestreamStatus.LIVE));
    }

    @PostMapping("/{id}/end")
    public ApiResponse<LivestreamResponse> end(@PathVariable String id) {
        return ApiResponse.success(service.transition(id, LivestreamStatus.ENDED));
    }

    record StatsRequest(long viewerCount, long uniqueViewerCount, long concurrentViewerCount) {}

    @PatchMapping("/{id}/stats")
    public ApiResponse<LivestreamResponse> stats(@PathVariable String id, @RequestBody StatsRequest request) {
        return ApiResponse.success(service.updateStats(id, request.viewerCount(), request.uniqueViewerCount(), request.concurrentViewerCount()));
    }

    public record RespondRequest(boolean accepted) {}

    /** KOL chấp nhận / từ chối lời mời live (lưu trong participantRefs.inviteStatus). */
    @PatchMapping("/{id}/respond")
    public ApiResponse<LivestreamResponse> respond(
            @PathVariable String id,
            @RequestBody RespondRequest request,
                    Jwt jwt) {
        return ApiResponse.success(service.respondInvite(id, request.accepted(), jwt));
    }
}
