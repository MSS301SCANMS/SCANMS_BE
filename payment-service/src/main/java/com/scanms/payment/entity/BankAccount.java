package com.scanms.payment.entity;

import com.scanms.payment.constant.BankAccountVerificationStatus;
import jakarta.persistence.*;
import jakarta.validation.constraints.AssertTrue;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "bank_accounts")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class BankAccount {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String bankAccountId;

    private String storeId;

    private String collaboratorId;

    private String bankCode;

    private String holderName;

    private String accountCiphertext;

    private String maskedNumber;

    @Enumerated(EnumType.STRING)
    private BankAccountVerificationStatus verificationStatus;

    private Boolean active;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @AssertTrue(message = "Exactly one of storeId or collaboratorId must be set")
    public boolean isOwnerValid() { return (storeId == null) != (collaboratorId == null); }
}
