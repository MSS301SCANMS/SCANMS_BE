package com.scanms.payment.repository;

import com.scanms.payment.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import java.util.Optional;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String>, JpaSpecificationExecutor<WalletTransaction> {
    boolean existsByIdempotencyKey(String idempotencyKey);
    Optional<WalletTransaction> findByIdempotencyKey(String key);
    Page<WalletTransaction> findByWalletId(String walletId, Pageable page);
}

