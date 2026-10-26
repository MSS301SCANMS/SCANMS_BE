package com.scanms.promotion.service;

import com.scanms.promotion.service.impl.ShortVideoReferralServiceImpl;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.constant.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.*;
import java.time.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class ShortVideoReferralServiceTest {
    @Mock ReferralLinkRepository links;
    @Mock CollaboratorProfileRepository profiles;
    @Mock PartnershipInvitationRepository invitations;
    @Mock ReferralClickRepository clicks;
    @Mock VoucherAccess access;
    @Mock ReferralProductAccess products;
    @Mock CommissionPolicyService policies;
    @InjectMocks ShortVideoReferralServiceImpl service;
    ReferralLink link;CollaboratorProfile profile;
    void valid(){link=ReferralLink.builder().referralLinkId("ref").collaboratorId("kol").productId("product").status(ReferralLinkStatus.ACTIVE).build();profile=CollaboratorProfile.builder().collaboratorId("kol").userId("creator").approvalStatus(CollaboratorStatus.APPROVED).build();when(links.findById("ref")).thenReturn(Optional.of(link));when(profiles.findById("kol")).thenReturn(Optional.of(profile));}
    @Test void clickTimeAndExpiryComeFromServerAndVideoMustContainExactLink(){
        valid();when(products.availableProduct("product")).thenReturn((Map)Map.of("storeId","store"));
        when(products.publicVideo("video")).thenReturn((Map)Map.of("collaboratorId","kol","products",List.of(Map.of("productId","product","referralLinkId","wrong"))));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.click(new ShortVideoReferralService.Click("product","ref","video")));
        verifyNoInteractions(clicks);
        when(clicks.save(any())).thenAnswer(i->i.getArgument(0));
        var before=Instant.now();var result=service.click(new ShortVideoReferralService.Click("product","ref",null));
        assertTrue(Instant.parse(result.get("expiresAt")).isAfter(before.plusSeconds(86399)));
        var captor=ArgumentCaptor.forClass(ReferralClick.class);verify(clicks).save(captor.capture());assertEquals(Duration.ofHours(24),Duration.between(captor.getValue().getClickedAt(),captor.getValue().getExpiresAt()));
    }
    @Test void validatesProductAndExpiryAndExcludesSelfPurchase(){
        var now=LocalDateTime.now(ZoneOffset.UTC);
        var click=ReferralClick.builder().id("click").productId("product").referralLinkId("ref").shortVideoId("video").clickedAt(now).expiresAt(now.plusHours(24)).build();
        when(clicks.findById("click")).thenReturn(Optional.of(click));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.verifyClick("click","other","buyer"));
        click.setExpiresAt(now.minusSeconds(1));assertTrue(service.verifyClick("click","product","buyer").isEmpty());
        click.setExpiresAt(now.plusHours(24));valid();assertTrue(service.verifyClick("click","product","creator").isEmpty());
        var result=service.verifyClick("click","product","buyer");assertEquals("ref",result.get("referralLinkId"));assertEquals("video",result.get("sourceVideoId"));assertEquals("click",result.get("referralClickId"));
    }
    @Test void videoPublishingRequiresAcceptedShopPartnership(){
        when(access.userId()).thenReturn("creator");when(profiles.findByUserId("creator")).thenReturn(Optional.of(CollaboratorProfile.builder().collaboratorId("kol").userId("creator").approvalStatus(CollaboratorStatus.APPROVED).build()));
        when(invitations.findByStoreIdInOrderByCreatedAtDesc(List.of("store"))).thenReturn(List.of());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.videoAccess("store",List.of(new ShortVideoReferralService.Link("product","ref"))));
        verifyNoInteractions(links,products);
    }
    @Test void acceptedPartnershipStillRequiresActiveCommissionPolicy(){
        valid();when(access.userId()).thenReturn("creator");when(profiles.findByUserId("creator")).thenReturn(Optional.of(profile));
        var invite=new PartnershipInvitation();invite.setCollaboratorId("kol");invite.setStatus("ACCEPTED");
        when(invitations.findByStoreIdInOrderByCreatedAtDesc(List.of("store"))).thenReturn(List.of(invite));
        when(products.availableProduct("product")).thenReturn((Map)Map.of("storeId","store"));
        var input=List.of(new ShortVideoReferralService.Link("product","ref"));
        var error=assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.videoAccess("store",input));
        assertTrue(error.getReason().contains("hoa hồng"));
        when(policies.resolvePolicy("kol",null,"product","store")).thenReturn(CommissionPolicy.builder().commissionRate(new java.math.BigDecimal("0.10")).build());
        assertEquals("kol",service.videoAccess("store",input).get("collaboratorId"));
    }
}
