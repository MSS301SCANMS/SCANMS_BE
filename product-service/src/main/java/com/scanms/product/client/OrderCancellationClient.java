package com.scanms.product.client;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@FeignClient(name="order-cancellation",url="${clients.order.url:http://localhost:8083}")
public interface OrderCancellationClient {
    @GetMapping("/api/v1/orders/{id}/voucher-restoration-eligibility")
    Map<String,Object> eligibility(@RequestHeader("Authorization") String authorization,@PathVariable String id);
}
