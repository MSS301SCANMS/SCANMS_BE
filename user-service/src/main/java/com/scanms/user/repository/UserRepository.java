package com.scanms.user.repository;

import com.scanms.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByIdentitySubject(String identitySubject);
    Optional<User> findByEmail(String email);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query("select u.status, count(u) from User u group by u.status")
    java.util.List<Object[]> countGroupByStatus();

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerHourSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerDaySince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);

    @org.springframework.data.jpa.repository.Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    java.util.List<Object[]> countPerMonthSince(@org.springframework.data.repository.query.Param("from") java.time.LocalDateTime from);
}
