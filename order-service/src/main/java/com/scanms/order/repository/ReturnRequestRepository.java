package com.scanms.order.repository;

import com.scanms.order.entity.ReturnRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReturnRequestRepository extends JpaRepository<ReturnRequest, UUID> {}
