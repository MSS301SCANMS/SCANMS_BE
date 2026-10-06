package com.scanms.order.controller;
import com.scanms.order.service.CheckoutService;
import com.scanms.order.dto.ApiResponse;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import java.util.Map;
@RestController @RequestMapping("/api/v1/checkout") @RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkout;
    @PostMapping public ApiResponse<Map<String,Object>> create(@Valid @RequestBody CheckoutService.Input input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.created(checkout.checkout(input,jwt)); }
    @PostMapping("/quote") public ApiResponse<Map<String,Object>> quote(@Valid @RequestBody CheckoutService.QuoteInput input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(checkout.quote(input,jwt)); }
}
