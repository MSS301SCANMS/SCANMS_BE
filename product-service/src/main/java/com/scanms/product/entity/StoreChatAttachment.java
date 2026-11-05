package com.scanms.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "store_chat_attachments", indexes = @Index(name = "idx_chat_attachment_conversation", columnList = "conversationId"))
@Getter
@Setter
public class StoreChatAttachment {
    @Id
    private String id;
    @Column(nullable = false)
    private String conversationId;
    @Column(nullable = false)
    private String uploaderId;
    @Column(nullable = false)
    private String fileName;
    @Column(nullable = false)
    private String contentType;
    @Column(nullable = false)
    private long sizeBytes;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false, columnDefinition = "bytea")
    private byte[] content;
}
