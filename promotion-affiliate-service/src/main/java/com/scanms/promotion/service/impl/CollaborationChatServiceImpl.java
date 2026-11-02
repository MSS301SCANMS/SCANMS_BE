package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CollaborationChatServiceImpl implements CollaborationChatService {
    private final ProductCommissionDealRepository deals;
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository partnerships;
    private final SampleRequestService samples;
    private final VoucherAccess access;

    @Override
    public Map<String, Object> resource(String type, String id) {
        if ("SAMPLE_REQUEST".equals(type)) {
            var row = samples.get(id);
            return Map.of("storeId", row.getStoreId(), "customerId", row.getCustomerId(), "sample", row);
        }
        if (!"COMMISSION_DEAL".equals(type)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
        var deal = deals.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        var profile = profiles.findById(deal.getCollaboratorId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!access.userId().equals(profile.getUserId())) access.requireStoreOwner(deal.getStoreId());
        return Map.of("storeId", deal.getStoreId(), "customerId", profile.getUserId(), "deal", deal);
    }

    @Override
    public Map<String, Object> partner(String storeId, String userId) {
        access.requireStoreOwner(storeId);
        var profile = profiles.findByUserId(userId).filter(p -> p.getApprovalStatus() == CollaboratorStatus.APPROVED)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN));
        boolean accepted = partnerships.findByStoreIdInOrderByCreatedAtDesc(List.of(storeId)).stream()
                .anyMatch(p -> profile.getCollaboratorId().equals(p.getCollaboratorId()) && "ACCEPTED".equals(p.getStatus()));
        if (!accepted) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chưa có hợp tác được chấp nhận.");
        return Map.of("storeId", storeId, "customerId", userId, "collaboratorId", profile.getCollaboratorId(), "name",
                profile.getApplicationData() == null ? "KOL" : Objects.toString(profile.getApplicationData().get("channelName"), "KOL"));
    }

    @Override
    public List<Map<String, Object>> partners(String storeId) {
        access.requireStoreOwner(storeId);
        return partnerships.findByStoreIdInOrderByCreatedAtDesc(List.of(storeId)).stream()
                .filter(p -> "ACCEPTED".equals(p.getStatus())).map(p -> p.getCollaboratorId()).distinct()
                .map(profiles::findById).flatMap(Optional::stream)
                .filter(p -> p.getApprovalStatus() == CollaboratorStatus.APPROVED)
                .map(p -> partner(storeId, p.getUserId())).toList();
    }
}
