package com.scanms.promotion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity @Table(name="referral_clicks") @Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReferralClick {
    @Id private String id;
    private String referralLinkId;
    private String productId;
    private String shortVideoId;
    private LocalDateTime clickedAt;
    private LocalDateTime expiresAt;
}
