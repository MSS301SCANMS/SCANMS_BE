package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.constant.ReferralLinkStatus;
import com.scanms.promotion.entity.ReferralLink;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.repository.PartnershipInvitationRepository;
import com.scanms.promotion.repository.ReferralLinkRepository;
import com.scanms.promotion.service.LivestreamAttributionSource;
import com.scanms.promotion.service.ReferralAttributionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReferralAttributionServiceImpl implements ReferralAttributionService {
    private final ReferralLinkRepository links;
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository invitations;
    private final LivestreamAttributionSource livestreams;

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> verify(String id, String productId, Jwt jwt) {
        requireOrder(jwt);
        var link = links.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Referral link not found"));
        return verified(link, productId);
    }

    @Override
    @Transactional
    public Map<String, String> livestream(String id, String productId, Jwt jwt) {
        requireOrder(jwt);
        var live = livestreams.session(id, jwt.getTokenValue());
        if (!"LIVE".equals(live.get("status")) || !(live.get("productRefs") instanceof Map<?, ?> products)
                || !(products.get("productIds") instanceof Collection<?> productIds) || !productIds.contains(productId))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Product is not available in this live session");
        if (!(live.get("collaboratorRefs") instanceof Map<?, ?> refs) || !(refs.get("collaboratorIds") instanceof Collection<?> ids) || ids.size() != 1)
            return Map.of();
        String collaboratorId = ids.iterator().next().toString(), storeId = Objects.toString(live.get("storeId"), "");
        if (!(live.get("participantRefs") instanceof Map<?, ?> participants)
                || !(participants.get("collaboratorResponses") instanceof Map<?, ?> responses) || !"ACCEPTED".equals(responses.get(collaboratorId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Livestream invitation has not been accepted");
        profiles.lockById(collaboratorId).filter(p -> p.getApprovalStatus() == CollaboratorStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Approved KOL required"));
        if (invitations.findByStoreIdInOrderByCreatedAtDesc(List.of(storeId)).stream()
                .noneMatch(invite -> collaboratorId.equals(invite.getCollaboratorId()) && "ACCEPTED".equals(invite.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Accepted shop partnership required");
        String token = "live-" + UUID.nameUUIDFromBytes((id + ":" + productId + ":" + collaboratorId).getBytes(StandardCharsets.UTF_8));
        var link = links.findByToken(token).orElseGet(() -> links.saveAndFlush(ReferralLink.builder()
                .collaboratorId(collaboratorId).productId(productId).livestreamId(id).token(token).label("Livestream checkout")
                .channel("LIVESTREAM").status(ReferralLinkStatus.ACTIVE).build()));
        return verified(link, productId);
    }

    private void requireOrder(Jwt jwt) {
        var roles = new HashSet<String>();
        if (jwt.getClaim("realm_access") instanceof Map<?, ?> realm && realm.get("roles") instanceof Collection<?> values)
            values.forEach(value -> roles.add(value.toString()));
        if (jwt.getClaim("resource_access") instanceof Map<?, ?> resources)
            resources.values().forEach(value -> {
                if (value instanceof Map<?, ?> client && client.get("roles") instanceof Collection<?> values)
                    values.forEach(role -> roles.add(role.toString()));
            });
        if (!roles.contains("ORDER_INTERNAL")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }

    private Map<String, String> verified(ReferralLink link, String productId) {
        if (!productId.equals(link.getProductId()) || link.getStatus() != ReferralLinkStatus.ACTIVE
                || (link.getExpiresAt() != null && !link.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Liên kết giới thiệu không hợp lệ hoặc đã hết hạn.");
        var profile = profiles.findById(link.getCollaboratorId()).filter(p -> p.getApprovalStatus() == CollaboratorStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "KOL chưa được duyệt hoặc đã bị ngừng hoạt động."));
        return Map.of("referralLinkId", link.getReferralLinkId(), "productId", productId,
                "collaboratorId", profile.getCollaboratorId(), "userId", profile.getUserId());
    }
}
