package com.scanms.promotion.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name="partnership_invitations", indexes={
    @Index(name="ix_partnership_store_created",columnList="store_id,created_at"),
    @Index(name="ix_partnership_kol_created",columnList="collaborator_id,created_at")
})
@Getter @Setter @NoArgsConstructor
public class PartnershipInvitation {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
    @Column(name="store_id", nullable=false) private String storeId;
    @Column(name="collaborator_id", nullable=false) private String collaboratorId;
    @Column(name="product_id") private String productId;
    private String storeName;
    private String collaboratorName;
    private String productName;
    @Column(length=2000) private String message;
    @Column(nullable=false) private String status;
    private String referralLinkId;
    private String referralToken;
    @Column(name="created_at",nullable=false) private LocalDateTime createdAt;
    private LocalDateTime respondedAt;
}
