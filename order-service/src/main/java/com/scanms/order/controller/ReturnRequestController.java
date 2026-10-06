package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateReturnRequest;
import com.scanms.order.dto.request.ReturnDecisionRequest;
import com.scanms.order.dto.response.ReturnRequestResponse;
import com.scanms.order.service.ReturnRequestService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/returns")
@RequiredArgsConstructor
public class ReturnRequestController {
    private final ReturnRequestService service;

    @org.springframework.security.access.prepost.PreAuthorize("@financeBoundary.canWrite(authentication)")
    @PostMapping
    public ResponseEntity<ApiResponse<ReturnRequestResponse>> create(@Valid @RequestBody CreateReturnRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @PostMapping("/{id}/decision")
    public ApiResponse<ReturnRequestResponse> processDecision(
            @PathVariable String id,
            @RequestBody(required = false) ReturnDecisionRequest decision,
            @RequestParam(required = false) com.scanms.order.constant.ReturnRequestStatus status,
            @RequestParam(required = false) String decisionReason) {
        ReturnDecisionRequest finalDecision = decision;
        if (finalDecision == null && status != null) {
            finalDecision = new ReturnDecisionRequest(status, decisionReason, null, null);
        }
        if (finalDecision == null) {
            throw new com.scanms.order.exception.AppException(com.scanms.order.exception.ErrorCode.INVALID_REQUEST, "Decision status is required");
        }
        return ApiResponse.success(service.processDecision(id, finalDecision));
    }

    @GetMapping("/by-order-item/{orderItemId}")
    public ApiResponse<List<ReturnRequestResponse>> findByOrderItemId(@PathVariable String orderItemId) {
        return ApiResponse.success(service.findByOrderItemId(orderItemId));
    }

    @GetMapping("/{id}")
    public ApiResponse<ReturnRequestResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    public ApiResponse<List<ReturnRequestResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
