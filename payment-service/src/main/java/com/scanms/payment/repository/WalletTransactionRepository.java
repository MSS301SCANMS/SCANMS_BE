package com.scanms.payment.repository;

import com.scanms.payment.entity.WalletTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, String> {
    boolean existsByIdempotencyKey(String idempotencyKey);
}

