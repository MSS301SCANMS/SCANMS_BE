package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.service.CollaborationChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/referrals/chat")
@RequiredArgsConstructor
public class CollaborationChatController {
    private final CollaborationChatService service;

    @GetMapping("/resources/{type}/{id}")
    public ApiResponse<Map<String, Object>> resource(@PathVariable String type, @PathVariable String id) {
        return ApiResponse.success(service.resource(type, id));
    }

    @GetMapping("/partner")
    public ApiResponse<Map<String, Object>> partner(@RequestParam String storeId, @RequestParam String userId) {
        return ApiResponse.success(service.partner(storeId, userId));
    }

    @GetMapping("/partners")
    public ApiResponse<List<Map<String, Object>>> partners(@RequestParam String storeId) {
        return ApiResponse.success(service.partners(storeId));
    }
}
