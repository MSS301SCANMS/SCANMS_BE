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
    private String returnRequestId;

    private String orderItemId;

    private Integer quantity;

    private String reason;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> evidenceRefs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> shopResponse;

    @Enumerated(EnumType.STRING)
    private ReturnRequestStatus status;

    @Column(length = 3000)
    private String decisionReason;

    @Column(name = "refund_amount_vnd")
    private Long refundAmountVnd;

    private String refundReference;

    @Column(length = 128)
    private String requestKey;

    /** Server-owned return transport facts, never copied from customer evidence. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> logistics;

    private LocalDateTime requestedAt;

    private LocalDateTime resolvedAt;
}
