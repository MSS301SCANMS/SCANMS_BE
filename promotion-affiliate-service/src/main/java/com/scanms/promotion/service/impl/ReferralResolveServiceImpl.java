package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.constant.ReferralLinkStatus;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.repository.ReferralLinkRepository;
import com.scanms.promotion.service.ReferralProductAccess;
import com.scanms.promotion.service.ReferralResolveService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReferralResolveServiceImpl implements ReferralResolveService {
    private final ReferralLinkRepository links;
    private final CollaboratorProfileRepository profiles;
    private final ReferralProductAccess products;

    @Override
    public Map<String, String> resolve(String token) {
        var link = links.findByToken(token).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Liên kết không tồn tại."));
        if (link.getStatus() != ReferralLinkStatus.ACTIVE || (link.getExpiresAt() != null
                && !link.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))))
            throw new ResponseStatusException(HttpStatus.GONE, "Liên kết đã hết hạn hoặc ngừng hoạt động.");
        if (profiles.findById(link.getCollaboratorId()).filter(profile -> profile.getApprovalStatus() == CollaboratorStatus.APPROVED).isEmpty())
            throw new ResponseStatusException(HttpStatus.GONE, "Liên kết hiện không khả dụng.");
        products.requireAvailable(link.getProductId());
        return Map.of("referralLinkId", link.getReferralLinkId(), "productId", link.getProductId());
    }
}
