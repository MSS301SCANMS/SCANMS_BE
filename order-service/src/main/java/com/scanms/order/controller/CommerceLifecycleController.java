package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.service.CommerceLifecycle;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/commerce") @RequiredArgsConstructor
public class CommerceLifecycleController {
    private final CommerceLifecycle lifecycle;
    @PostMapping("/seller-orders/{id}/confirm") public Object confirm(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.confirm(id,jwt)); }
    @PostMapping("/seller-orders/{id}/dispatch") public Object dispatch(@PathVariable String id,@Valid @RequestBody CommerceLifecycle.Dispatch input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.dispatch(id,input,jwt)); }
    @PostMapping("/seller-orders/{id}/delivered") public Object delivered(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.delivered(id,jwt)); }
    @GetMapping("/shipments/{code}") public Object track(@PathVariable String code,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.track(code,jwt)); }
    @PostMapping("/seller-orders/{id}/simulation/start") public Object start(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.simulate(id,false,jwt)); }
    @PostMapping("/seller-orders/{id}/simulation/complete") public Object complete(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.simulate(id,true,jwt)); }
    @PostMapping("/orders/{id}/receipt") public Object receipt(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.receipt(id,jwt)); }
    @PostMapping("/returns") public Object createReturn(@Valid @RequestBody CommerceLifecycle.ReturnInput input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.created(lifecycle.createReturn(input,jwt)); }
    @GetMapping("/returns") public Object myReturns(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.myReturns(jwt)); }
    @GetMapping("/returns/{id}") public Object detail(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.getReturn(id,jwt)); }
    @PostMapping("/returns/{id}/cancel") public Object cancel(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.cancelReturn(id,jwt)); }
    @PostMapping("/returns/{id}/shipment") public Object send(@PathVariable String id,@Valid @RequestBody CommerceLifecycle.Dispatch input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.sendReturn(id,input,jwt)); }
    public record Event(String status) {}
    @PostMapping("/returns/{id}/simulation") public Object simulate(@PathVariable String id,@RequestBody Event input,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.simulateReturn(id,input.status(),jwt)); }
    @PostMapping("/returns/{id}/receipt") public Object receive(@PathVariable String id,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(lifecycle.receiveReturn(id,jwt)); }
}
