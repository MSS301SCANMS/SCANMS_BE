package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CommissionRepository extends JpaRepository<Commission, UUID> {}
