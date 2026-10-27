package com.scanms.product.controller;

import com.scanms.product.dto.ApiResponse;
import com.scanms.product.entity.StoreChatMessage;
import com.scanms.product.service.StoreChatService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/chat/conversations")
@RequiredArgsConstructor
public class StoreChatController {
    private final StoreChatService chat;
    public record OpenRequest(@NotBlank String storeId) {}
    public record MessageRequest(@NotBlank String messageText, String messageId) {}
    @GetMapping public ApiResponse<List<Map<String,Object>>> list() { return ApiResponse.success(chat.list()); }
    @PostMapping public ApiResponse<Map<String,Object>> open(@Valid @RequestBody OpenRequest request) { return ApiResponse.success(chat.open(request.storeId())); }
    @GetMapping("/{id}/messages") public ApiResponse<List<Map<String,Object>>> history(@PathVariable String id,
            @RequestParam(defaultValue = "50") int take, @RequestParam(required = false) String cursor) {
        return ApiResponse.success(chat.history(id, take, cursor).stream().map(chat::messageDto).toList());
    }
    @PostMapping("/{id}/messages") public ApiResponse<Map<String,Object>> send(@PathVariable String id, @Valid @RequestBody MessageRequest request) {
        return ApiResponse.created(chat.messageDto(chat.send(id, request.messageText(), request.messageId())));
    }
    @PatchMapping("/{id}/read") public ApiResponse<Void> read(@PathVariable String id) { chat.read(id); return ApiResponse.success(null); }
}
