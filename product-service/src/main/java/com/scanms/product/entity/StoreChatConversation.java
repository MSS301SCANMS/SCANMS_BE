package com.scanms.product.entity;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "store_chat_conversations", uniqueConstraints = @UniqueConstraint(name = "uk_store_chat_customer", columnNames = {"store_id", "customer_id"}))
@Getter @Setter
public class StoreChatConversation {
    @Id private String id;
    @Column(nullable = false) private String storeId;
    @Column(nullable = false) private String customerId;
    private String customerName;
    @Column(nullable = false) private Instant createdAt;
    @Column(nullable = false) private Instant updatedAt;
}
