package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@FeignClient(name = "product-service", url = "${clients.product.url:http://localhost:8082}")
public interface ProductClient {
    @GetMapping("/api/v1/products/{id}")
    Map<String, Object> getProduct(@PathVariable String id);
}
