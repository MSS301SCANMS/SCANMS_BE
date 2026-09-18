package com.scanms.promotion.entity;

import com.scanms.promotion.constant.ReferralLinkStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "referral_links")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReferralLink {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID referralLinkId;

    private UUID collaboratorId;

    private UUID productId;

    private String token;

    @Enumerated(EnumType.STRING)
    private ReferralLinkStatus status;

    private LocalDateTime expiresAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
