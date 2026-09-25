package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CommissionRepository extends JpaRepository<Commission, String> {}
