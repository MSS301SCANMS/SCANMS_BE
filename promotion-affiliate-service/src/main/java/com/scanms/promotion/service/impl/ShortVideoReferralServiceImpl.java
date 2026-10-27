package com.scanms.promotion.service.impl;

import com.scanms.promotion.service.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.constant.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor
public class ShortVideoReferralServiceImpl implements ShortVideoReferralService {
    private final ReferralLinkRepository links;
    private final CollaboratorProfileRepository profiles;
    private final PartnershipInvitationRepository invitations;
    private final ReferralClickRepository clicks;
    private final VoucherAccess access;
    private final ReferralProductAccess products;
    private final CommissionPolicyService policies;

    @Override @Transactional(readOnly=true)
    public Map<String,String> videoAccess(String storeId,List<Link> selected) {
        var profile=profiles.findByUserId(access.userId()).filter(p->p.getApprovalStatus()==CollaboratorStatus.APPROVED)
                .orElseThrow(()->failure("Hồ sơ KOL cần được duyệt trước khi đăng video."));
        requirePartnership(storeId,profile.getCollaboratorId());
        for(var l:selected) {
            var link=validLink(l.referralLinkId(),l.productId());
            if(!profile.getCollaboratorId().equals(link.getCollaboratorId())) throw failure("Liên kết không thuộc KOL hiện tại.");
            if(!storeId.equals(products.availableProduct(l.productId()).get("storeId"))) throw failure("Sản phẩm không thuộc shop hợp tác.");
            requireCommission(profile.getCollaboratorId(),l.productId(),storeId);
        }
        var data=profile.getApplicationData();
        String name=data==null?"KOL":Objects.toString(data.get("channelName"),Objects.toString(data.get("fullName"),"KOL"));
        return Map.of("userId",profile.getUserId(),"collaboratorId",profile.getCollaboratorId(),"creatorName",name);
    }

    @Override @Transactional
    public Map<String,String> click(Click input) {
        var link=validLink(input.referralLinkId(),input.productId());
        Map<?,?> product=products.availableProduct(input.productId());
        if(input.shortVideoId()!=null) {
            var video=products.publicVideo(input.shortVideoId());
            if(!link.getCollaboratorId().equals(video.get("collaboratorId"))||!(video.get("products") instanceof Collection<?> items)
                    || items.stream().noneMatch(x->x instanceof Map<?,?> row && input.productId().equals(row.get("productId")) && input.referralLinkId().equals(row.get("referralLinkId"))))
                throw failure("Video không gắn liên kết sản phẩm này.");
            requirePartnership(product.get("storeId").toString(),link.getCollaboratorId());
            requireCommission(link.getCollaboratorId(),input.productId(),product.get("storeId").toString());
        }
        var now=LocalDateTime.now(ZoneOffset.UTC);
        var click=clicks.save(ReferralClick.builder().id(UUID.randomUUID().toString()).referralLinkId(link.getReferralLinkId())
                .productId(input.productId()).shortVideoId(input.shortVideoId()).clickedAt(now).expiresAt(now.plusHours(24)).build());
        return Map.of("clickId",click.getId(),"referralLinkId",click.getReferralLinkId(),"expiresAt",click.getExpiresAt().toInstant(ZoneOffset.UTC).toString());
    }

    @Override @Transactional(readOnly=true)
    public Map<String,String> verifyClick(String id,String productId,String customerId) {
        var click=clicks.findById(id).orElseThrow(()->failure("Không tìm thấy lượt giới thiệu."));
        if(!productId.equals(click.getProductId())) throw failure("Lượt giới thiệu không thuộc sản phẩm.");
        // An expired click must not prevent the customer's purchase; simply omit commission.
        if(!click.getExpiresAt().isAfter(LocalDateTime.now(ZoneOffset.UTC))) return Map.of();
        // The link must have been valid when this server-recorded click occurred.
        // Its configured expiry must not shorten the click's existing 24-hour window.
        if (click.getClickedAt() == null || click.getClickedAt().isAfter(LocalDateTime.now(ZoneOffset.UTC)))
            throw failure("Thời điểm lượt giới thiệu không hợp lệ.");
        var link=validLink(click.getReferralLinkId(),productId,click.getClickedAt());
        var profile=profiles.findById(link.getCollaboratorId()).orElseThrow();
        if(profile.getUserId().equals(customerId)) return Map.of();
        var result=new LinkedHashMap<String,String>();
        result.put("referralLinkId",link.getReferralLinkId());result.put("collaboratorId",link.getCollaboratorId());
        result.put("referralClickId",click.getId());result.put("clickedAt",click.getClickedAt().toInstant(ZoneOffset.UTC).toString());
        if(click.getShortVideoId()!=null) result.put("sourceVideoId",click.getShortVideoId());
        return result;
    }
    private ReferralLink validLink(String id,String productId) {
        return validLink(id,productId,LocalDateTime.now(ZoneOffset.UTC));
    }
    private ReferralLink validLink(String id,String productId,LocalDateTime at) {
        var link=links.findById(id).orElseThrow(()->failure("Liên kết giới thiệu không tồn tại."));
        if(!productId.equals(link.getProductId())||link.getStatus()!=ReferralLinkStatus.ACTIVE
                || link.getExpiresAt()!=null&&!link.getExpiresAt().isAfter(at)) throw failure("Liên kết giới thiệu không còn hiệu lực.");
        profiles.findById(link.getCollaboratorId()).filter(p->p.getApprovalStatus()==CollaboratorStatus.APPROVED).orElseThrow(()->failure("KOL chưa được duyệt."));
        return link;
    }
    private void requirePartnership(String store,String collaborator) {
        if(invitations.findByStoreIdInOrderByCreatedAtDesc(List.of(store)).stream().noneMatch(i->collaborator.equals(i.getCollaboratorId())&&"ACCEPTED".equals(i.getStatus())))
            throw failure("Shop và KOL cần chấp nhận hợp tác trước.");
    }
    private void requireCommission(String collaborator,String product,String store) {
        var policy=policies.resolvePolicy(collaborator,null,product,store);
        if(policy==null||policy.getCommissionRate()==null||policy.getCommissionRate().signum()<=0)
            throw failure("Shop cần cấu hình chính sách hoa hồng còn hiệu lực cho sản phẩm trước khi đăng video tiếp thị.");
    }
    private ResponseStatusException failure(String message) {return new ResponseStatusException(HttpStatus.CONFLICT,message);}
}
