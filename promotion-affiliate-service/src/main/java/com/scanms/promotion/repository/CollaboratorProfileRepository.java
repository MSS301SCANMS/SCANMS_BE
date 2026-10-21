package com.scanms.promotion.repository;

import com.scanms.promotion.entity.CollaboratorProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import com.scanms.promotion.constant.CollaboratorStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface CollaboratorProfileRepository extends JpaRepository<CollaboratorProfile, String> {
    List<CollaboratorProfile> findByApprovalStatus(CollaboratorStatus status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CollaboratorProfile c where c.collaboratorId = :id")
    Optional<CollaboratorProfile> lockById(@Param("id") String id);
    Optional<CollaboratorProfile> findByUserId(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from CollaboratorProfile c where c.userId = :userId")
    Optional<CollaboratorProfile> lockByUserId(@Param("userId") String userId);
}
