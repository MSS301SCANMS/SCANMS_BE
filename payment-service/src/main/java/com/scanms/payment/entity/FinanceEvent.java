package com.scanms.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.Map;

@Entity @Table(name="finance_events", uniqueConstraints=@UniqueConstraint(columnNames="eventKey"))
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class FinanceEvent {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String eventId;
    private String eventKey;
    private String destination;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb") private Map<String,Object> payload;
    private Instant createdAt;
    private Instant deliveredAt;
    private Instant nextAttemptAt;
    private int attempts;
    @Column(length=1000) private String lastError;
    private Integer lastHttpStatus;
    @Builder.Default private boolean blocked=false;
    @Version private Long version;
}
