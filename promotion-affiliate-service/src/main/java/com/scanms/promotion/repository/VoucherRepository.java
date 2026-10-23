package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRepository extends JpaRepository<Voucher, String> {
    boolean existsByCodeIgnoreCase(String code);
    java.util.List<Voucher> findByStoreId(String storeId);
}
