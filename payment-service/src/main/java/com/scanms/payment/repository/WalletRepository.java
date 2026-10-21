package com.scanms.payment.repository;

import com.scanms.payment.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;
import com.scanms.payment.constant.WalletOwnerType;

public interface WalletRepository extends JpaRepository<Wallet, String>, JpaSpecificationExecutor<Wallet> {
    Optional<Wallet> findByOwnerTypeAndOwnerRefIdAndCurrency(WalletOwnerType type, String ownerRefId, String currency);
    List<Wallet> findByOwnerTypeAndOwnerRefId(WalletOwnerType type, String ownerRefId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.walletId = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") String id);
}

