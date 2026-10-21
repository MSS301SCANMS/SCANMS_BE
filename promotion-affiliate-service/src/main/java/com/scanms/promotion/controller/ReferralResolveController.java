package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.ReferralProductAccess;
import java.time.*;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequiredArgsConstructor
public class ReferralResolveController {
    private final ReferralLinkRepository links;
    private final CollaboratorProfileRepository profiles;
    private final ReferralProductAccess products;

    @GetMapping("/api/v1/referrals/resolve/{token}")
    public ApiResponse<Map<String, String>> resolve(@PathVariable String token) {
        var link = links.findByToken(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liên kết không tồn tại."));
        if (link.getStatus() != ReferralLinkStatus.ACTIVE || (link.getExpiresAt() != null
                && !link.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))))
            throw new ResponseStatusException(HttpStatus.GONE, "Liên kết đã hết hạn hoặc ngừng hoạt động.");
        if (profiles.findById(link.getCollaboratorId()).filter(profile -> profile.getApprovalStatus() == CollaboratorStatus.APPROVED).isEmpty())
            throw new ResponseStatusException(HttpStatus.GONE, "Liên kết hiện không khả dụng.");
        products.requireAvailable(link.getProductId());
        return ApiResponse.success(Map.of("referralLinkId", link.getReferralLinkId(), "productId", link.getProductId()));
    }
}
