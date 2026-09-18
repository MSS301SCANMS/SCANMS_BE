package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface VoucherRepository extends JpaRepository<Voucher, UUID> {}
