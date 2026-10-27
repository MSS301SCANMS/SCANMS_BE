package com.scanms.product.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "store_chat_messages", indexes = @Index(name = "idx_store_chat_messages", columnList = "conversationId,createdAt"))
@Getter @Setter
public class StoreChatMessage {
    @Id private String id;
    @Column(nullable = false) private String conversationId;
    @Column(nullable = false) private String senderId;
    @Column(nullable = false, length = 10000) private String messageText;
    @Column(nullable = false) private Instant createdAt;
    @com.fasterxml.jackson.annotation.JsonProperty("isRead")
    @Column(nullable = false) private boolean isRead;
}
