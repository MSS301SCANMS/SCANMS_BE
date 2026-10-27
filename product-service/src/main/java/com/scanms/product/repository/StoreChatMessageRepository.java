package com.scanms.product.repository;

import com.scanms.product.entity.StoreChatMessage;
import java.util.List;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StoreChatMessageRepository extends JpaRepository<StoreChatMessage, String> {
    @Query(value = "SELECT * FROM store_chat_messages WHERE conversation_id = :id ORDER BY created_at DESC, id DESC LIMIT :take OFFSET :skip", nativeQuery = true)
    List<StoreChatMessage> latest(@Param("id") String id, @Param("take") int take, @Param("skip") int skip);
    @Query(value = "SELECT m.* FROM store_chat_messages m JOIN store_chat_messages c ON c.id = :cursor AND c.conversation_id = :id WHERE m.conversation_id = :id AND (m.created_at < c.created_at OR (m.created_at = c.created_at AND m.id < c.id)) ORDER BY m.created_at DESC, m.id DESC LIMIT :take", nativeQuery = true)
    List<StoreChatMessage> before(@Param("id") String id, @Param("cursor") String cursor, @Param("take") int take);
    long countByConversationIdAndSenderIdNotAndIsReadFalse(String conversationId, String senderId);
    @Modifying
    @Query("update StoreChatMessage m set m.isRead = true where m.conversationId = :id and m.senderId <> :userId and m.isRead = false")
    int read(@Param("id") String id, @Param("userId") String userId);
}
