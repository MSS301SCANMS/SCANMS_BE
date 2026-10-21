package com.scanms.promotion.repository;

import com.scanms.promotion.entity.CollaboratorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CollaboratorProfileRepository extends JpaRepository<CollaboratorProfile, String> {
    java.util.List<CollaboratorProfile> findByApprovalStatus(com.scanms.promotion.constant.CollaboratorStatus status);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CollaboratorProfile c where c.collaboratorId = :id")
    Optional<CollaboratorProfile> lockById(@org.springframework.data.repository.query.Param("id") String id);
    Optional<CollaboratorProfile> findByUserId(String userId);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select c from CollaboratorProfile c where c.userId = :userId")
    Optional<CollaboratorProfile> lockByUserId(@org.springframework.data.repository.query.Param("userId") String userId);
}
