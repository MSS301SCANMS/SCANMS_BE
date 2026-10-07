package com.scanms.promotion.repository;

import com.scanms.promotion.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CommissionRepository extends JpaRepository<Commission, String> {

    /** Lấy tất cả commission của một OrderItem (thường chỉ có 1) */
    List<Commission> findByOrderItemId(String orderItemId);

    /** Kiểm tra tránh duplicate commission cho cùng item + collaborator */
    Optional<Commission> findByOrderItemIdAndCollaboratorId(String orderItemId, String collaboratorId);
}
