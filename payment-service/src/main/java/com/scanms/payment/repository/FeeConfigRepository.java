package com.scanms.payment.repository;

import com.scanms.payment.entity.FeeConfig;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeeConfigRepository extends JpaRepository<FeeConfig, String> {}

