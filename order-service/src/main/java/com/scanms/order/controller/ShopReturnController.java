package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.service.ShopReturnService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController @RequiredArgsConstructor @RequestMapping("/api/v1/returns")
@PreAuthorize("@financeBoundary.canRespondReturn(authentication)")
public class ShopReturnController {
    private final ShopReturnService service;
    @GetMapping("/shop")
    public ApiResponse<List<ShopReturnService.View>> list(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(jwt));
    }
    @PostMapping("/{id}/shop-response")
    public ApiResponse<ShopReturnService.View> respond(@PathVariable String id,@Valid @RequestBody ShopReturnService.ResponseInput input,@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.respond(id,input,jwt));
    }
}
