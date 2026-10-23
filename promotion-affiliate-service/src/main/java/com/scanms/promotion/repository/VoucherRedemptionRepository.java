package com.scanms.promotion.repository;

import com.scanms.promotion.entity.VoucherRedemption;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface VoucherRedemptionRepository extends JpaRepository<VoucherRedemption, String> {
    Optional<VoucherRedemption> findByOrderId(String orderId);
}

