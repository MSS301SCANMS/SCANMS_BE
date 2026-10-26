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
    private final com.scanms.product.service.LiveMetricsSignature metricsSignature;
    private final com.scanms.product.service.LivestreamViewerService viewers;
    private final com.scanms.product.service.LivestreamReactionService reactions;

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
    public ApiResponse<LivestreamResponse> stats(@PathVariable String id, @RequestBody StatsRequest request,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Time",defaultValue="") String timestamp,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Signature",defaultValue="") String signature) {
        metricsSignature.require("PATCH\n"+id+"\n"+request.viewerCount()+"\n"+request.uniqueViewerCount()+"\n"+request.concurrentViewerCount(),timestamp,signature);
        return ApiResponse.success(service.updateStats(id, request.viewerCount(), request.uniqueViewerCount(), request.concurrentViewerCount()));
    }

    public record ViewRequest(@jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{1,100}") @jakarta.validation.constraints.NotBlank String visitId) {}
    @PostMapping("/{id}/views")
    public ApiResponse<Boolean> view(@PathVariable String id,@Valid @RequestBody ViewRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Time",defaultValue="") String timestamp,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Signature",defaultValue="") String signature){
        metricsSignature.require("POST\n"+id+"\n"+request.visitId(),timestamp,signature);
        viewers.record(id,request.visitId(),jwt);return ApiResponse.success(true);
    }

    public record RespondRequest(boolean accepted) {}
    @PatchMapping("/{id}/reactions/stats")
    public ApiResponse<com.scanms.product.dto.response.LivestreamReactionCountResponse> reactionStats(
            @PathVariable String id,@Valid @RequestBody com.scanms.product.dto.request.UpdateLivestreamReactionCountRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Time",defaultValue="") String timestamp,
            @org.springframework.web.bind.annotation.RequestHeader(value="X-Live-Metrics-Signature",defaultValue="") String signature) {
        metricsSignature.require("LIKES\n"+id+"\n"+request.likeCount(),timestamp,signature);
        return ApiResponse.success(new com.scanms.product.dto.response.LivestreamReactionCountResponse(reactions.updateCount(id,request.likeCount(),jwt)));
    }
    public record PinRequest(@jakarta.validation.constraints.Pattern(regexp="[A-Za-z0-9_-]{1,100}") String productId) {}

    @PostMapping("/{id}/pin")
    public ApiResponse<LivestreamResponse> pin(@PathVariable String id,@Valid @RequestBody PinRequest request) {
        return ApiResponse.success(service.pinProduct(id,request.productId()));
    }

    /** KOL chấp nhận / từ chối lời mời live (lưu trong participantRefs.inviteStatus). */
    @PatchMapping("/{id}/respond")
    public ApiResponse<LivestreamResponse> respond(
            @PathVariable String id,
            @RequestBody RespondRequest request,
            @org.springframework.security.core.annotation.AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.respondInvite(id, request.accepted(), jwt));
    }
}
