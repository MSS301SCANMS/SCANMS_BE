package com.scanms.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Entity @Table(name="short_videos")
@Getter @Setter @Builder @NoArgsConstructor @AllArgsConstructor
public class ShortVideo {
    @Id @GeneratedValue(strategy=GenerationType.UUID) private String id;
    private String creatorUserId;
    private String collaboratorId;
    private String creatorName;
    private String storeId;
    private String title;
    @Column(length=2000) private String caption;
    @Column(length=1500) private String videoUrl;
    @Column(length=1500) private String posterUrl;
    @Column(length=500,unique=true) private String publicId;
    private double durationSeconds;
    private String status;
    @JdbcTypeCode(SqlTypes.JSON) @Column(columnDefinition="jsonb")
    private List<Map<String,String>> productLinks;
    @CreationTimestamp private LocalDateTime createdAt;
    @Version private Long version;
}
