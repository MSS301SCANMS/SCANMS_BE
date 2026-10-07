package com.scanms.payment.repository;

import com.scanms.payment.entity.Wallet;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface WalletRepository extends JpaRepository<Wallet, String>, org.springframework.data.jpa.repository.JpaSpecificationExecutor<Wallet> {
    Optional<Wallet> findByOwnerTypeAndOwnerRefIdAndCurrency(com.scanms.payment.constant.WalletOwnerType type, String ownerRefId, String currency);
    java.util.List<Wallet> findByOwnerTypeAndOwnerRefId(com.scanms.payment.constant.WalletOwnerType type, String ownerRefId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select w from Wallet w where w.walletId = :id")
    Optional<Wallet> findByIdForUpdate(@Param("id") String id);
}

