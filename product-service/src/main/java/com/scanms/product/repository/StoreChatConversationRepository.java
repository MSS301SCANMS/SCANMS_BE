package com.scanms.product.repository;

import com.scanms.product.entity.StoreChatConversation;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

public interface StoreChatConversationRepository extends JpaRepository<StoreChatConversation, String> {
    @Modifying
    @Query(value = "INSERT INTO store_chat_conversations (id, store_id, customer_id, customer_name, created_at, updated_at) VALUES (:id, :storeId, :customerId, :name, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP) ON CONFLICT (store_id, customer_id) DO NOTHING", nativeQuery = true)
    int openIfAbsent(@Param("id") String id, @Param("storeId") String storeId, @Param("customerId") String customerId, @Param("name") String name);
    @Query("select c from StoreChatConversation c where c.customerId = :userId or c.storeId in (select s.storeId from Store s where s.ownerUserId = :userId) order by c.updatedAt desc")
    List<StoreChatConversation> visible(@Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from StoreChatConversation c where c.id = :id")
    Optional<StoreChatConversation> locked(@Param("id") String id);
}
