package com.scanms.order.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.UUID;

@FeignClient(name = "catalog-service", url = "${clients.catalog.url:http://localhost:8082}")
public interface CatalogClient {
    @GetMapping("/api/v1/products/{id}")
    Map<String, Object> getProduct(@PathVariable UUID id);
}
