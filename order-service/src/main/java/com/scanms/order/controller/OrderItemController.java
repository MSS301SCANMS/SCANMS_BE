package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.dto.request.CreateOrderItemRequest;
import com.scanms.order.dto.response.OrderItemResponse;
import com.scanms.order.service.OrderItemService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/order-items")
@RequiredArgsConstructor
public class OrderItemController {
    private final OrderItemService service;

    @PostMapping
    ResponseEntity<ApiResponse<OrderItemResponse>> create(@Valid @RequestBody CreateOrderItemRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.created(service.create(request)));
    }

    @GetMapping("/{id}")
    ApiResponse<OrderItemResponse> getById(@PathVariable String id) {
        return ApiResponse.success(service.getById(id));
    }

    @GetMapping
    ApiResponse<List<OrderItemResponse>> findAll() {
        return ApiResponse.success(service.findAll());
    }
}
