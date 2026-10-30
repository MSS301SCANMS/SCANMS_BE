package com.scanms.order.controller;

import com.scanms.order.dto.ApiResponse;
import com.scanms.order.service.PublicProductSales;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/store-sales")
@RequiredArgsConstructor
public class PublicProductSalesController {
    private final PublicProductSales service;

    @GetMapping("/products")
    public ApiResponse<Map<String, Long>> sold(@RequestParam List<String> productIds) {
        return ApiResponse.success(service.soldQuantities(productIds));
    }
}
