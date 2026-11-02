package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.dto.request.StoreChatMessageRequest;
import com.scanms.product.dto.request.StoreChatOpenRequest;
import com.scanms.product.service.StoreChatService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class StoreChatController {
    private final StoreChatService chat;

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list() {
        return ApiResponse.success(chat.list());
    }

    @PostMapping
    public ApiResponse<Map<String, Object>> open(@Valid @RequestBody StoreChatOpenRequest request) {
        return ApiResponse.success(chat.open(request.storeId(), request.customerId()));
    }

    @GetMapping("/identity")
    public ApiResponse<Map<String, Object>> identity() {
        return ApiResponse.success(chat.identity());
    }

    @GetMapping("/{id}/subscription")
    public ApiResponse<Map<String, Object>> subscription(@PathVariable String id) {
        return ApiResponse.success(chat.subscription(id));
    }

    @GetMapping("/{id}/messages")
    public ApiResponse<List<Map<String, Object>>> history(@PathVariable String id,
                                                          @RequestParam(defaultValue = "50") int take, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(chat.history(id, take, cursor).stream().map(chat::messageDto).toList());
    }

    @PostMapping("/{id}/messages")
    public ApiResponse<Map<String, Object>> send(@PathVariable String id, @Valid @RequestBody StoreChatMessageRequest request) {
        return ApiResponse.created(chat.messageDto(chat.send(id, request.messageText(), request.messageId())));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> read(@PathVariable String id) {
        chat.read(id);
        return ApiResponse.success(null);
    }
}
