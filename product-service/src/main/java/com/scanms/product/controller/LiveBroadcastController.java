package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.LiveBroadcastHeartbeat;
import com.scanms.product.service.LiveBroadcastService;
import com.scanms.product.service.LiveMetricsSignature;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/livestreams")
@RequiredArgsConstructor
public class LiveBroadcastController {
    private final LiveBroadcastService service;
    private final LiveMetricsSignature signature;

    @PostMapping("/{id}/broadcast")
    public ApiResponse<Map<String, Object>> heartbeat(@PathVariable String id, @Valid @RequestBody LiveBroadcastHeartbeat input,
                                                      @AuthenticationPrincipal Jwt jwt,
                                                      @RequestHeader(value = "X-Live-Metrics-Time", defaultValue = "") String time,
                                                      @RequestHeader(value = "X-Live-Metrics-Signature", defaultValue = "") String proof) {
        signature.require("BROADCAST\n" + id + "\n" + input.publisherId() + "\n" + input.epoch() + "\n" + input.sequence() + "\n" + input.state(), time, proof);
        return ApiResponse.success(service.heartbeat(id, input, jwt.getSubject()));
    }
}
