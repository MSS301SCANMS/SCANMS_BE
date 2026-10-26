package com.scanms.promotion.service.impl;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.repository.CollaboratorProfileRepository;
import com.scanms.promotion.repository.PartnershipInvitationRepository;
import com.scanms.promotion.service.LivestreamPartnershipService;
import com.scanms.promotion.service.VoucherAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LivestreamPartnershipServiceImpl implements LivestreamPartnershipService {
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository invitations;
    private final VoucherAccess access;

    @Override
    public Map<String, Object> verify(String storeId, String collaboratorId) {
        var profile = profiles.findById(collaboratorId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        String user = access.userId();
        if (!profile.getUserId().equals(user)) access.requireStoreOwner(storeId);
        else access.requireApprovedStore(storeId);
        if (profile.getApprovalStatus() != CollaboratorStatus.APPROVED || invitations.findByCollaboratorIdOrderByCreatedAtDesc(collaboratorId).stream()
                .noneMatch(invitation -> storeId.equals(invitation.getStoreId()) && "ACCEPTED".equals(invitation.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "KOL phải được duyệt và chấp nhận hợp tác với shop trước khi tham gia livestream.");
        return Map.of("collaboratorId", collaboratorId, "userId", profile.getUserId(), "storeId", storeId, "approvalStatus", "APPROVED");
    }
}
