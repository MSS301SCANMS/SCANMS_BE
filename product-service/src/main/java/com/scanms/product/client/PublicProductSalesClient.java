package com.scanms.product.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@FeignClient(name = "public-product-sales", url = "${clients.order.url:http://localhost:8083}")
public interface PublicProductSalesClient {
    @GetMapping("/api/v1/store-sales/products")
    Map<String, Object> sold(@RequestParam("productIds") List<String> productIds);
}
