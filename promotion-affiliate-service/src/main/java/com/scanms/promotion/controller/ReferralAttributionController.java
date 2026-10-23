package com.scanms.promotion.controller;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@RestController @RequiredArgsConstructor
public class ReferralAttributionController {
    private final ReferralLinkRepository links;
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository invitations;
    private final com.scanms.promotion.service.LivestreamAttributionSource livestreams;
    @GetMapping("/api/v1/referrals/attribution/{id}")
    public ApiResponse<Map<String,String>> verify(@PathVariable String id,@RequestParam String productId,@AuthenticationPrincipal Jwt jwt) {
        requireOrder(jwt);
        var link=links.findById(id).orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Referral link not found"));
        return verified(link,productId);
    }
    private void requireOrder(Jwt jwt) {
        var roles=new HashSet<String>();
        if(jwt.getClaim("realm_access") instanceof Map<?,?> realm && realm.get("roles") instanceof Collection<?> values) values.forEach(value->roles.add(value.toString()));
        if(jwt.getClaim("resource_access") instanceof Map<?,?> resources) resources.values().forEach(value->{if(value instanceof Map<?,?> client && client.get("roles") instanceof Collection<?> values) values.forEach(role->roles.add(role.toString()));});
        if(!roles.contains("ORDER_INTERNAL")) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    private ApiResponse<Map<String,String>> verified(com.scanms.promotion.entity.ReferralLink link,String productId) {
        if(!productId.equals(link.getProductId()) || link.getStatus()!=ReferralLinkStatus.ACTIVE || (link.getExpiresAt()!=null&&!link.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Liên kết giới thiệu không hợp lệ hoặc đã hết hạn.");
        var profile=profiles.findById(link.getCollaboratorId()).filter(p->p.getApprovalStatus()==CollaboratorStatus.APPROVED)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"KOL chưa được duyệt hoặc đã bị ngừng hoạt động."));
        return ApiResponse.success(Map.of("referralLinkId",link.getReferralLinkId(),"productId",productId,"collaboratorId",profile.getCollaboratorId(),"userId",profile.getUserId()));
    }
    @GetMapping("/api/v1/referrals/livestream-attribution/{id}")
    @org.springframework.transaction.annotation.Transactional
    public ApiResponse<Map<String,String>> livestream(@PathVariable String id,@RequestParam String productId,@AuthenticationPrincipal Jwt jwt) {
        requireOrder(jwt);
        var live=livestreams.session(id,jwt.getTokenValue());
        if(!"LIVE".equals(live.get("status")) || !(live.get("productRefs") instanceof Map<?,?> products)
                || !(products.get("productIds") instanceof Collection<?> productIds) || !productIds.contains(productId))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Product is not available in this live session");
        // A multi-host session needs an explicit referral; never choose a beneficiary arbitrarily.
        if(!(live.get("collaboratorRefs") instanceof Map<?,?> refs) || !(refs.get("collaboratorIds") instanceof Collection<?> ids) || ids.size()!=1)
            return ApiResponse.success(Map.of());
        String collaboratorId=ids.iterator().next().toString(),storeId=Objects.toString(live.get("storeId"),"");
        if(!(live.get("participantRefs") instanceof Map<?,?> participants)
                || !(participants.get("collaboratorResponses") instanceof Map<?,?> responses) || !"ACCEPTED".equals(responses.get(collaboratorId)))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Livestream invitation has not been accepted");
        profiles.lockById(collaboratorId).filter(p->p.getApprovalStatus()==CollaboratorStatus.APPROVED)
                .orElseThrow(()->new ResponseStatusException(HttpStatus.CONFLICT,"Approved KOL required"));
        if(invitations.findByStoreIdInOrderByCreatedAtDesc(List.of(storeId)).stream()
                .noneMatch(invite->collaboratorId.equals(invite.getCollaboratorId()) && "ACCEPTED".equals(invite.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Accepted shop partnership required");
        String token="live-"+UUID.nameUUIDFromBytes((id+":"+productId+":"+collaboratorId).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var link=links.findByToken(token).orElseGet(()->links.saveAndFlush(com.scanms.promotion.entity.ReferralLink.builder()
                .collaboratorId(collaboratorId).productId(productId).livestreamId(id).token(token).label("Livestream checkout")
                .channel("LIVESTREAM").status(ReferralLinkStatus.ACTIVE).build()));
        return verified(link,productId);
    }
}
