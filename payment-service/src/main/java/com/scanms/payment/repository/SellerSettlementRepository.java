package com.scanms.payment.repository;

import com.scanms.payment.entity.SellerSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface SellerSettlementRepository extends JpaRepository<SellerSettlement, String> {
    Optional<SellerSettlement> findBySellerOrderId(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerSettlement s where s.sellerOrderId = :id")
    Optional<SellerSettlement> lockBySellerOrderId(@Param("id") String id);
    Page<SellerSettlement> findByStoreId(String id, Pageable page);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from SellerSettlement s where s.settlementId = :id")
    Optional<SellerSettlement> lockById(@Param("id") String id);
}
