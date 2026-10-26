package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CheckoutRequest;
import com.scanms.order.dto.request.CreateOrderRequest;
import com.scanms.order.dto.response.CheckoutResponse;
import com.scanms.order.dto.response.OrderResponse;
import com.scanms.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.repository.SellerOrderRepository;
import org.springframework.security.access.prepost.PreAuthorize;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {
    private final OrderService service;
    private final SellerOrderRepository sellerOrders;
    private final com.scanms.order.service.OrderCancellationPolicy cancellationPolicy;

    @Value("${clients.user.url:http://localhost:8081}")
    private String userUrl;

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
        // Checkout lưu customerId = userId nghiệp vụ, không phải jwt.sub (identitySubject)
        String effectiveCustomerId = customerId;
        if ((effectiveCustomerId == null || effectiveCustomerId.isBlank()) && jwt != null) {
            effectiveCustomerId = resolveBusinessUserId(jwt);
        }
        return ApiResponse.success(service.getMyOrders(effectiveCustomerId));
    }

    @SuppressWarnings("unchecked")
    private String resolveBusinessUserId(Jwt jwt) {
        try {
            Map<?, ?> envelope = RestClient.create().get()
                    .uri(userUrl + "/api/v1/users")
                    .headers(h -> h.setBearerAuth(jwt.getTokenValue()))
                    .retrieve()
                    .body(Map.class);
            Object result = envelope == null ? null : envelope.get("result");
            if (result instanceof Map<?, ?> user && user.get("userId") != null) {
                Object identity = user.get("identitySubject");
                if (identity != null && !jwt.getSubject().equals(String.valueOf(identity))) {
                    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active customer profile required");
                }
                return String.valueOf(user.get("userId"));
            }
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Cannot resolve customer profile", ex);
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Active customer profile required");
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
        return ApiResponse.success(Map.of("eligible", cancellationPolicy.canRestoreResources(id)));
    }

    @GetMapping
    public ApiResponse<List<OrderResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
