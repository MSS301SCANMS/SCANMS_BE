package com.scanms.order.repository;

import com.scanms.order.entity.SellerOrder;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface SellerOrderRepository extends JpaRepository<SellerOrder, UUID> {}
