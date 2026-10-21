package com.scanms.payment.repository;
import com.scanms.payment.entity.FinanceEvent;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface FinanceEventRepository extends JpaRepository<FinanceEvent,String> {
    boolean existsByEventKey(String key);
    Page<FinanceEvent> findByDeliveredAtIsNull(Pageable pageable);
    List<FinanceEvent> findTop20ByDeliveredAtIsNullAndBlockedFalseAndNextAttemptAtLessThanEqualOrderByCreatedAtAsc(Instant now);
    @Lock(LockModeType.PESSIMISTIC_WRITE) @Query("select e from FinanceEvent e where e.eventId=:id")
    Optional<FinanceEvent> lockById(@Param("id") String id);
}
