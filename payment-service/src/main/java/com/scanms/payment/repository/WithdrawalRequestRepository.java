package com.scanms.payment.repository;

import com.scanms.payment.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {
    java.util.List<WithdrawalRequest> findTop20ByStatusInAndScheduledForLessThanEqualOrderByScheduledForAsc(java.util.Collection<com.scanms.payment.constant.WithdrawalStatus> statuses, java.time.Instant now);
    java.util.Optional<WithdrawalRequest> findByIdempotencyKey(String key);
    org.springframework.data.domain.Page<WithdrawalRequest> findByWalletId(String walletId, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select w from WithdrawalRequest w where w.withdrawalId = :id")
    java.util.Optional<WithdrawalRequest> lockById(@org.springframework.data.repository.query.Param("id") String id);
}

