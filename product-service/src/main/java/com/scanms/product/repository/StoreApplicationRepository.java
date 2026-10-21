package com.scanms.product.repository;

import com.scanms.product.entity.StoreApplication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreApplicationRepository extends JpaRepository<StoreApplication, String> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from StoreApplication a where a.applicationId = :id")
    java.util.Optional<StoreApplication> lockById(@org.springframework.data.repository.query.Param("id") String id);
    boolean existsByApplicantUserIdAndStatus(String applicantUserId, com.scanms.product.constant.StoreApplicationStatus status);
}

