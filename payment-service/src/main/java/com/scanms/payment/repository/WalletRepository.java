package com.scanms.payment.repository;

import com.scanms.payment.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WalletRepository extends JpaRepository<Wallet, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.walletId = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") String id);
}

