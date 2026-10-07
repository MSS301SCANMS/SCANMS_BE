package com.scanms.promotion.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
@Entity @Table(name="commission_finalizations") @Getter @Setter @NoArgsConstructor
public class CommissionFinalization {
    @Id private String orderItemId;
    private Long amountVnd;
    private Instant finalizedAt;
    @Column(length=500) private String reason;
    private String finalizedBy;
}
