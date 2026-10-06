package com.scanms.payment.repository;

import com.scanms.payment.entity.SellerSettlement;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SellerSettlementRepository extends JpaRepository<SellerSettlement, String> {
    java.util.Optional<SellerSettlement> findBySellerOrderId(String id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SellerSettlement s where s.sellerOrderId = :id")
    java.util.Optional<SellerSettlement> lockBySellerOrderId(@org.springframework.data.repository.query.Param("id") String id);
    org.springframework.data.domain.Page<SellerSettlement> findByStoreId(String id, org.springframework.data.domain.Pageable page);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from SellerSettlement s where s.settlementId = :id")
    java.util.Optional<SellerSettlement> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
