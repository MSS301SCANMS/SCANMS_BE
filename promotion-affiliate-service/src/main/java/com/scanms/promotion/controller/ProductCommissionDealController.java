package com.scanms.promotion.controller;

import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.entity.ProductCommissionDeal;
import com.scanms.promotion.service.ProductCommissionDealService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/referrals/commission-deals")
@RequiredArgsConstructor
public class ProductCommissionDealController {
    private final ProductCommissionDealService service;
    public record Proposal(@NotBlank String productId, @NotNull @Min(1) @Max(3) Integer extraPoints, @NotBlank String message) {}
    public record Decision(@NotBlank String decision, String response) {}
    @GetMapping("/mine") public ApiResponse<List<ProductCommissionDeal>> mine() { return ApiResponse.success(service.mine()); }
    @GetMapping("/shop") public ApiResponse<List<ProductCommissionDeal>> shop(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.shop(jwt)); }
    @GetMapping("/rate") public ApiResponse<ProductCommissionDealService.Rate> rate(@RequestParam String productId) { return ApiResponse.success(service.quote(productId)); }
    @PostMapping public ApiResponse<ProductCommissionDeal> create(@Valid @RequestBody Proposal input) {
        return ApiResponse.created(service.propose(input.productId(), input.extraPoints(), input.message()));
    }
    @PostMapping("/{id}/respond") public ApiResponse<ProductCommissionDeal> respond(@PathVariable String id, @Valid @RequestBody Decision input) {
        return ApiResponse.success(service.respond(id, input.decision(), input.response()));
    }
    @PostMapping("/{id}/cancel") public ApiResponse<ProductCommissionDeal> cancel(@PathVariable String id) { return ApiResponse.success(service.cancel(id)); }
}
