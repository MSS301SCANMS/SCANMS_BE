package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CheckoutRequest;
import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.CheckoutResponse;
import com.scanms.order.dto.response.OrderResponse;
import com.scanms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService service;

    @PreAuthorize("@financeBoundary.canWrite(authentication)")
    @PostMapping("/checkout")
    public ResponseEntity<ApiResponse<CheckoutResponse>> checkout(
            @Valid @RequestBody CheckoutRequest request,
            @AuthenticationPrincipal Jwt jwt) {
        String customerId = request.customerId();
        if (jwt != null && (customerId == null || customerId.isBlank())) {
            customerId = jwt.getSubject();
        }
        CheckoutRequest finalReq = new CheckoutRequest(
                customerId,
                request.idempotencyKey(),
                request.customerName(),
                request.customerPhone(),
                request.customerEmail(),
                request.shippingAddress(),
                request.paymentMethod(),
                request.currency(),
                request.voucherCode(),
                request.voucherDiscountVnd(),
                request.voucherFundingSource(),
                request.note(),
                request.shippingSnapshot(),
                request.items()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.checkout(finalReq)));
    }

    @GetMapping("/track")
    public ApiResponse<Map<String, Object>> track(
            @RequestParam(required = false) String phone,
            @RequestParam(required = false) String orderSn) {
        return ApiResponse.success(service.trackOrders(phone, orderSn));
    }

    @GetMapping("/my-orders")
    public ApiResponse<List<OrderResponse>> getMyOrders(
            @RequestParam(required = false) String customerId,
            @AuthenticationPrincipal Jwt jwt) {
        String effectiveCustomerId = customerId;
        if ((effectiveCustomerId == null || effectiveCustomerId.isBlank()) && jwt != null) {
            effectiveCustomerId = service.resolveBusinessUserId(jwt);
        }
        return ApiResponse.success(service.getMyOrders(effectiveCustomerId));
    }

    @GetMapping("/{id}/checkout-details")
    public ApiResponse<CheckoutResponse> getCheckoutDetails(@PathVariable String id) {
        return ApiResponse.success(service.getCheckoutDetails(id));
    }

    @PreAuthorize("@financeBoundary.canWrite(authentication)")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> create(@Valid @RequestBody CreateOrderRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @PreAuthorize("@financeBoundary.canWrite(authentication)")
    @GetMapping("/{id}/voucher-restoration-eligibility")
    public ApiResponse<Map<String, Boolean>> voucherRestorationEligibility(@PathVariable String id) {
        return ApiResponse.success(service.voucherRestorationEligibility(id));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
