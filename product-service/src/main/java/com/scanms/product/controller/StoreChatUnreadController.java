package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.service.StoreChatService;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
public class StoreChatUnreadController {
    private final StoreChatService chat;
    @GetMapping("/unread-count") public ApiResponse<Long> unread() {
        long count = chat.list().stream().mapToLong(c -> ((Number)((Map<?,?>)c.get("_count")).get("chatMessages")).longValue()).sum();
        return ApiResponse.success(count);
    }
}
