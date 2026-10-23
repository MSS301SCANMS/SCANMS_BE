package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Voucher;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface VoucherRepository extends JpaRepository<Voucher, String> {
    boolean existsByCodeIgnoreCase(String code);
    List<Voucher> findByStoreId(String storeId);
}
