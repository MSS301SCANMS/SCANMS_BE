package com.scanms.user.repository;

import com.scanms.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.*;
import java.time.LocalDateTime;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, String>, JpaSpecificationExecutor<User> {
    Optional<User> findByIdentitySubject(String identitySubject);
    Optional<User> findByEmail(String email);

    // --- Thống kê admin (COUNT/GROUP BY trong DB) ---

    long countByCreatedAtGreaterThanEqual(LocalDateTime from);

    @Query("select u.status, count(u) from User u group by u.status")
    List<Object[]> countGroupByStatus();

    @Query(value = "select date_trunc('hour', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerHourSince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('day', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerDaySince(@Param("from") LocalDateTime from);

    @Query(value = "select date_trunc('month', created_at) as bucket, count(*) as total from users where created_at >= :from group by 1", nativeQuery = true)
    List<Object[]> countPerMonthSince(@Param("from") LocalDateTime from);
}
