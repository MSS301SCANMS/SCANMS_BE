package com.scanms.order.repository;

import com.scanms.order.entity.Shipment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, String> {
    Optional<Shipment> findBySellerOrderId(String sellerOrderId);
}

