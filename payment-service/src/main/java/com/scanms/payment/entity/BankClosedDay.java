package com.scanms.payment.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="bank_closed_days") @Getter @Setter @NoArgsConstructor
public class BankClosedDay {
    @Id private LocalDate closedDate;
    @Column(nullable=false,length=500) private String reason;
    @Column(nullable=false) private String configuredBy;
    @Column(nullable=false) private Instant configuredAt;
}
