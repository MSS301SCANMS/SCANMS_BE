package com.scanms.promotion.repository;

import com.scanms.promotion.constant.CommissionPolicyScope;
import com.scanms.promotion.constant.CommissionPolicyStatus;
import com.scanms.promotion.entity.CommissionPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CommissionPolicyRepository extends JpaRepository<CommissionPolicy, String> {

    /** Scope COLLABORATOR — policy riêng cho từng KOL */
    List<CommissionPolicy> findByCollaboratorIdAndScopeTypeAndStatusOrderByPriorityDesc(
            String collaboratorId, CommissionPolicyScope scopeType, CommissionPolicyStatus status);

    /** Scope LIVESTREAM — policy gắn với một buổi live cụ thể */
    List<CommissionPolicy> findByLivestreamIdAndScopeTypeAndStatusOrderByPriorityDesc(
            String livestreamId, CommissionPolicyScope scopeType, CommissionPolicyStatus status);

    /** Scope PRODUCT — policy áp dụng cho một sản phẩm cụ thể */
    List<CommissionPolicy> findByProductIdAndScopeTypeAndStatusOrderByPriorityDesc(
            String productId, CommissionPolicyScope scopeType, CommissionPolicyStatus status);

    /** Scope STORE — policy áp dụng cho toàn bộ store */
    List<CommissionPolicy> findByStoreIdAndScopeTypeAndStatusOrderByPriorityDesc(
            String storeId, CommissionPolicyScope scopeType, CommissionPolicyStatus status);

    /** Scope PLATFORM — fallback mặc định toàn hệ thống */
    List<CommissionPolicy> findByScopeTypeAndStatusOrderByPriorityDesc(
            CommissionPolicyScope scopeType, CommissionPolicyStatus status);
}

