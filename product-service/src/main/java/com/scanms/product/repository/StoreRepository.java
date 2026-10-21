package com.scanms.product.repository;

import com.scanms.product.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, String> {
    // Heartbeats must not increment Store.version or change settings' updatedAt.
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("update Store s set s.lastActiveAt=:now where s.ownerUserId=:owner and s.approvalStatus=:status and (s.lastActiveAt is null or s.lastActiveAt<:now)")
    int touchPresence(@org.springframework.data.repository.query.Param("owner") String owner,
        @org.springframework.data.repository.query.Param("status") com.scanms.product.constant.StoreStatus status,
        @org.springframework.data.repository.query.Param("now") java.time.Instant now);
    java.util.List<Store> findByOwnerUserId(String ownerUserId);
}
