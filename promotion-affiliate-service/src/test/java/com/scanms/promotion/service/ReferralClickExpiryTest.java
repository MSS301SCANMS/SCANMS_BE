package com.scanms.promotion.service;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.entity.*;
import com.scanms.promotion.repository.*;
import com.scanms.promotion.service.impl.ShortVideoReferralServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.time.*;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ReferralClickExpiryTest {
    private final ReferralLinkRepository links = mock(ReferralLinkRepository.class);
    private final CollaboratorProfileRepository profiles = mock(CollaboratorProfileRepository.class);
    private final ReferralClickRepository clicks = mock(ReferralClickRepository.class);
    private final ShortVideoReferralService service = new ShortVideoReferralServiceImpl(links, profiles,
            mock(PartnershipInvitationRepository.class), clicks, mock(VoucherAccess.class),
            mock(ReferralProductAccess.class), mock(CommissionPolicyService.class));

    private void fixture(ReferralLinkStatus status, LocalDateTime clickedAt, LocalDateTime clickExpiry) {
        linksResult(status, LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5));
        when(clicks.findById("click")).thenReturn(Optional.of(ReferralClick.builder().id("click")
                .referralLinkId("ref").productId("product").clickedAt(clickedAt).expiresAt(clickExpiry).build()));
        var profile = new CollaboratorProfile();
        profile.setCollaboratorId("kol"); profile.setUserId("creator"); profile.setApprovalStatus(CollaboratorStatus.APPROVED);
        when(profiles.findById("kol")).thenReturn(Optional.of(profile));
    }
    private void linksResult(ReferralLinkStatus status, LocalDateTime expiry) {
        when(links.findById("ref")).thenReturn(Optional.of(ReferralLink.builder().referralLinkId("ref")
                .productId("product").collaboratorId("kol").status(status).expiresAt(expiry).build()));
    }
    @Test void previouslyValidClickSurvivesLinkExpiry() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        fixture(ReferralLinkStatus.ACTIVE, now.minusMinutes(10), now.plusHours(23));
        assertEquals("ref", service.verifyClick("click", "product", "customer").get("referralLinkId"));
    }
    @Test void expiredClickDoesNotEarnCommissionOrBlockPurchase() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        fixture(ReferralLinkStatus.ACTIVE, now.minusHours(25), now.minusHours(1));
        assertTrue(service.verifyClick("click", "product", "customer").isEmpty());
    }
    @Test void clickAfterLinkExpiryIsRejected() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        fixture(ReferralLinkStatus.ACTIVE, now.minusMinutes(1), now.plusHours(23));
        assertThrows(ResponseStatusException.class, () -> service.verifyClick("click", "product", "customer"));
    }
    @Test void expiredLinkRejectsNewClicks() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        fixture(ReferralLinkStatus.ACTIVE, now.minusMinutes(10), now.plusHours(23));
        assertThrows(ResponseStatusException.class, () -> service.click(new ShortVideoReferralService.Click("product", "ref", null)));
    }
    @Test void wrongProductAndSelfReferralDoNotEarnCommission() {
        var now = LocalDateTime.now(ZoneOffset.UTC);
        fixture(ReferralLinkStatus.ACTIVE, now.minusMinutes(10), now.plusHours(23));
        assertThrows(ResponseStatusException.class, () -> service.verifyClick("click", "other", "customer"));
        assertTrue(service.verifyClick("click", "product", "creator").isEmpty());
    }
}
