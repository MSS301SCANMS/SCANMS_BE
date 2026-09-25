package com.scanms.product.entity;

import com.scanms.product.constant.FeedbackStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.*;
import org.hibernate.type.SqlTypes;
import java.time.Instant;
import java.util.*;

@Entity
@Table(name = "feedbacks")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class Feedback {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private String feedbackId;
    @Column(nullable = false)
    private String productId;
    @Column(nullable = false)
    private String orderItemId;
    @Column(nullable = false)
    private String customerId;
    @Column(nullable = false)
    private Integer rating;
    private String comment;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition = "jsonb")
    private List<String> imageRefs;
    @Enumerated(EnumType.STRING) @Column(nullable = false)
    private FeedbackStatus status;
    @CreationTimestamp @Column(updatable = false)
    private Instant createdAt;
    @UpdateTimestamp
    private Instant updatedAt;
}

