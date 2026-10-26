package com.scanms.product.controller;

import com.scanms.product.dto.*;
import com.scanms.product.dto.ShortVideoContracts.*;
import com.scanms.product.service.ShortVideoService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController @RequiredArgsConstructor
public class ShortVideoController {
    private final ShortVideoService service;
    @GetMapping("/api/v1/public/short-videos") public ApiResponse<Feed> feed(@RequestParam(defaultValue="0") int page) {return ApiResponse.success(service.feed(page));}
    @GetMapping("/api/v1/public/short-videos/{id}") public ApiResponse<View> one(@PathVariable String id) {return ApiResponse.success(service.publicVideo(id));}
    @GetMapping("/api/v1/short-videos/mine") public ApiResponse<Feed> mine(@RequestParam(defaultValue="0") int page) {return ApiResponse.success(service.mine(page));}
    @PostMapping("/api/v1/short-videos") public ApiResponse<View> create(@Valid @RequestBody Create input) {return ApiResponse.created(service.create(input));}
    @DeleteMapping("/api/v1/short-videos/{id}") public ApiResponse<Boolean> hide(@PathVariable String id) {service.hide(id);return ApiResponse.success(true);}
}
