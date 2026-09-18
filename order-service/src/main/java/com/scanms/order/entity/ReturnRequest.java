package com.scanms.order.entity;

import com.scanms.order.constant.ReturnRequestStatus;
import jakarta.persistence.*;

import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

@Entity
@Table(name = "return_requests")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ReturnRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID returnRequestId;

    private UUID orderItemId;

    private Integer quantity;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> evidenceRefs;

    @Enumerated(EnumType.STRING)
    private ReturnRequestStatus status;

    private String decisionReason;

    private Long refundAmount;

    private String refundReference;

    private LocalDateTime requestedAt;

    private LocalDateTime resolvedAt;
}
