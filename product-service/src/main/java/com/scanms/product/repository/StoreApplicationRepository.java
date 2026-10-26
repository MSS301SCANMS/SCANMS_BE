package com.scanms.product.repository;

import com.scanms.product.entity.StoreApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import com.scanms.product.constant.StoreApplicationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface StoreApplicationRepository extends JpaRepository<StoreApplication, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from StoreApplication a where a.applicationId = :id")
    Optional<StoreApplication> lockById(@Param("id") String id);
    boolean existsByApplicantUserIdAndStatus(String applicantUserId, StoreApplicationStatus status);
}

