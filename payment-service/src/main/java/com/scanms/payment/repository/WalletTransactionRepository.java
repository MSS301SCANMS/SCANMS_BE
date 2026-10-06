package com.scanms.payment.repository;

import com.scanms.payment.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<WalletTransaction> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    java.util.Optional<WalletTransaction> findByIdempotencyKey(String key);
    org.springframework.data.domain.Page<WalletTransaction> findByWalletId(String walletId, org.springframework.data.domain.Pageable page);
}

