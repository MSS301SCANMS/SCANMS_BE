package com.scanms.payment.repository;

import com.scanms.payment.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import com.scanms.payment.constant.WithdrawalStatus;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {
    List<WithdrawalRequest> findTop20ByStatusInAndScheduledForLessThanEqualOrderByScheduledForAsc(Collection<WithdrawalStatus> statuses, Instant now);
    Optional<WithdrawalRequest> findByIdempotencyKey(String key);
    Page<WithdrawalRequest> findByWalletId(String walletId, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from WithdrawalRequest w where w.withdrawalId = :id")
    Optional<WithdrawalRequest> lockById(@Param("id") String id);
}

