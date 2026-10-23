package com.scanms.promotion.controller;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.dto.ApiResponse;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.VoucherAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

/** Private verification for Product without exposing KYC or banking details. */
@RestController @RequiredArgsConstructor
public class LivestreamPartnershipController {
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository invitations;
    private final VoucherAccess access;
    @GetMapping("/api/v1/referrals/livestream-access")
    public ApiResponse<Map<String,Object>> verify(@RequestParam String storeId,@RequestParam String collaboratorId) {
        var profile=profiles.findById(collaboratorId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
        String user=access.userId();
        if(!profile.getUserId().equals(user)) access.requireStoreOwner(storeId);
        else access.requireApprovedStore(storeId);
        if(profile.getApprovalStatus()!=CollaboratorStatus.APPROVED || invitations.findByCollaboratorIdOrderByCreatedAtDesc(collaboratorId).stream()
                .noneMatch(invitation->storeId.equals(invitation.getStoreId())&&"ACCEPTED".equals(invitation.getStatus())))
            throw new ResponseStatusException(HttpStatus.CONFLICT,"KOL phải được duyệt và chấp nhận hợp tác với shop trước khi tham gia livestream.");
        return ApiResponse.success(Map.of("collaboratorId",collaboratorId,"userId",profile.getUserId(),"storeId",storeId,"approvalStatus","APPROVED"));
    }
}
