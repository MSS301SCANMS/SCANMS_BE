package com.scanms.promotion.service.impl;

import com.scanms.promotion.service.PartnershipInvitationService;
import com.scanms.promotion.service.PartnershipInvitationService.Creator;
import com.scanms.promotion.service.PartnershipInvitationService.Product;
import com.scanms.promotion.service.PartnershipInvitationService.Catalog;
import com.scanms.promotion.service.PartnershipInvitationService.View;

import com.scanms.promotion.constant.CollaboratorStatus;
import com.scanms.promotion.constant.ReferralLinkStatus;
import com.scanms.promotion.dto.request.CreateReferralLinkRequest;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class PartnershipInvitationServiceImpl implements PartnershipInvitationService {
    private final PartnershipInvitationRepository invitations;
    private final CollaboratorProfileRepository profiles;
    private final VoucherAccess access;
    private final CollaborationAccess catalogAccess;
    private final ReferralLinkService referrals;

                       String productId,String productName,String message,String status,String referralLinkId,
                       String referralToken,LocalDateTime createdAt,LocalDateTime respondedAt) {}

    public Catalog catalog(Jwt jwt) {
        String userId=access.userId();
        var store=catalogAccess.stores(jwt,false).stream().filter(s->"APPROVED".equals(s.get("approvalStatus"))).findFirst()
            .orElseThrow(()->fail(HttpStatus.CONFLICT,"Bạn cần có gian hàng đã được duyệt để mời KOL."));
        String storeId=text(store,"storeId");
        // Only public channel information is exposed; identity documents and banking data never leave this boundary.
        var creators=profiles.findByApprovalStatus(CollaboratorStatus.APPROVED).stream().filter(p->!userId.equals(p.getUserId()))
            .map(p->{var d=p.getApplicationData()==null?Map.<String,Object>of():p.getApplicationData();
                return new Creator(p.getCollaboratorId(),Objects.toString(d.get("channelName"),"KOL #"+p.getCollaboratorId().substring(0,8)),
                    Objects.toString(d.get("platform"),"OTHER"),d.get("followerCount") instanceof Number n?n.longValue():0,text(d,"channelUrl"));})
            .sorted(Comparator.comparing(Creator::name).thenComparing(Creator::id)).toList();
        return new Catalog(storeId,text(store,"name"),List.of(),creators);
    }

    public List<View> shop(Jwt jwt) {
        access.userId();
        var ids=catalogAccess.stores(jwt,false).stream().map(s->text(s,"storeId")).toList();
        return ids.isEmpty()?List.of():invitations.findByStoreIdInOrderByCreatedAtDesc(ids).stream().map(this::view).toList();
    }
    public List<View> mine() {
        var profile=profiles.findByUserId(access.userId()).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"Bạn chưa có hồ sơ KOL."));
        return invitations.findByCollaboratorIdOrderByCreatedAtDesc(profile.getCollaboratorId()).stream().map(this::view).toList();
    }

    @Transactional
    public View create(Jwt jwt,String storeId,String collaboratorId,String productId,String message) {
        String invitationMessage=message==null || message.isBlank()
            ? "Chào bạn, shop mong muốn kết nối và trao đổi cơ hội hợp tác cùng bạn."
            : message.trim();
        if(invitationMessage.length()>2000)
            throw fail(HttpStatus.BAD_REQUEST,"Lời nhắn không được vượt quá 2.000 ký tự.");
        String userId=access.userId();access.requireStoreOwner(storeId);
        var profile=profiles.lockById(collaboratorId).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy KOL."));
        if(profile.getApprovalStatus()!=CollaboratorStatus.APPROVED || userId.equals(profile.getUserId()))
            throw fail(HttpStatus.CONFLICT,"Chỉ có thể mời KOL khác đã được duyệt.");
        Map<?,?> product=null;
        if(
                productId!=null
                )product=access.storeProducts(
                storeId
                ).stream(
                
                ).filter(
                p->productId.equals(p.get("productId")) && "ACTIVE".equals(p.get("status"))
                ).findFirst(
                
                )
            .orElseThrow(()->fail(HttpStatus.CONFLICT,"Sản phẩm phải đang hoạt động và thuộc gian hàng của bạn."));
        var store=catalogAccess.stores(jwt,false).stream().filter(s->storeId.equals(s.get("storeId"))).findFirst()
            .orElseThrow(()->fail(HttpStatus.FORBIDDEN,"Bạn không sở hữu gian hàng này."));
        if(invitations.existsByStoreIdAndCollaboratorIdAndProductIdAndStatusIn(storeId,collaboratorId,productId,List.of("PENDING","ACCEPTED")))
            throw fail(HttpStatus.CONFLICT,"Đã có lời mời đang chờ hoặc hợp tác đã được đồng ý với KOL này. Hãy kiểm tra danh sách đã gửi.");
        var row=new PartnershipInvitation();
        row.setStoreId(storeId);
        row.setCollaboratorId(collaboratorId);
        row.setProductId(productId);
        row.setStoreName(text(store,"name"));row.setProductName(product==null?null:text(product,"name"));
        row.setCollaboratorName(profile.getApplicationData()==null?"KOL":Objects.toString(profile.getApplicationData().get("channelName"),"KOL"));
        row.setMessage(invitationMessage);row.setStatus("PENDING");row.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC));
        return view(invitations.saveAndFlush(row));
    }

    @Transactional
    public View respond(String id,String decision) {
        String userId=access.userId();
        // Same lock order as referral creation and invitation creation: profile, then invitation.
        var profile=profiles.lockByUserId(userId).orElseThrow(()->fail(HttpStatus.FORBIDDEN,"Bạn chưa có hồ sơ KOL."));
        if(profile.getApprovalStatus()!=CollaboratorStatus.APPROVED)throw fail(HttpStatus.FORBIDDEN,"Hồ sơ KOL chưa được duyệt hoặc đã bị tạm ngừng.");
        var row=locked(id);
        if(!profile.getCollaboratorId().equals(row.getCollaboratorId()))throw fail(HttpStatus.FORBIDDEN,"Lời mời không thuộc tài khoản của bạn.");
        if(!Set.of("ACCEPTED","REJECTED").contains(decision))throw fail(HttpStatus.BAD_REQUEST,"Phản hồi chưa hợp lệ.");
        if(decision.equals(row.getStatus()))return view(row);
        if(!"PENDING".equals(row.getStatus()))throw fail(HttpStatus.CONFLICT,"Lời mời đã được xử lý. Vui lòng tải lại.");
        if("ACCEPTED".equals(decision)) {
            access.requireApprovedStore(row.getStoreId());
            if(row.getProductId()!=null) {
                access.requireProductStore(row.getProductId(),row.getStoreId());
                var link=referrals.create(new CreateReferralLinkRequest(profile.getCollaboratorId(),row.getProductId(),null,
                    "invite-"+row.getId(),ReferralLinkStatus.ACTIVE,null,"Hợp tác từ gian hàng","OTHER"));
                row.setReferralLinkId(link.referralLinkId());row.setReferralToken(link.token());
            }
        }
        row.setStatus(decision);row.setRespondedAt(LocalDateTime.now(ZoneOffset.UTC));
        return view(invitations.saveAndFlush(row));
    }

    @Transactional
    public View cancel(String id) {
        var row=locked(id);access.requireStoreOwner(row.getStoreId());
        if("CANCELLED".equals(row.getStatus()))return view(row);
        if(!"PENDING".equals(row.getStatus()))throw fail(HttpStatus.CONFLICT,"Chỉ có thể thu hồi lời mời đang chờ phản hồi.");
        row.setStatus("CANCELLED");
        row.setRespondedAt(LocalDateTime.now(ZoneOffset.UTC));
        return view(invitations.saveAndFlush(row));
    }
    private PartnershipInvitation locked(String id){return invitations.lockById(id).orElseThrow(()->fail(HttpStatus.NOT_FOUND,"Không tìm thấy lời mời."));}
    private View view(
            PartnershipInvitation r
            ){return new View(r.getId(),
                    r.getStoreId(),
                    r.getStoreName(),
                    r.getCollaboratorId(),
                    r.getCollaboratorName(),
                    r.getProductId(),
                    r.getProductName(),
                    r.getMessage(),
                    r.getStatus(),
                    r.getReferralLinkId(),
                    r.getReferralToken(),
                    r.getCreatedAt(),
                    r.getRespondedAt());}
    private static String text(Map<?,?> m,String k){return Objects.toString(m.get(k),null);}
    private static ResponseStatusException fail(HttpStatus s,String m){return new ResponseStatusException(s,m);}
}
