package com.scanms.payment.repository;

import com.scanms.payment.entity.SellerSettlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SellerSettlementRepository extends JpaRepository<SellerSettlement, UUID> {}
