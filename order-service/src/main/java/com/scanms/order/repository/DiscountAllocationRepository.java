package com.scanms.order.repository;

import com.scanms.order.entity.DiscountAllocation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DiscountAllocationRepository extends JpaRepository<DiscountAllocation, String> {
    List<DiscountAllocation> findByOrderItemId(String orderItemId);
}

