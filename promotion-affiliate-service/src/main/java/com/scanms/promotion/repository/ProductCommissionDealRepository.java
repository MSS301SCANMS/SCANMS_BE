package com.scanms.promotion.repository;

import com.scanms.promotion.entity.ProductCommissionDeal;
import java.util.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface ProductCommissionDealRepository extends JpaRepository<ProductCommissionDeal,String> {
    List<ProductCommissionDeal> findByCollaboratorIdOrderByCreatedAtDesc(String id);
    List<ProductCommissionDeal> findByStoreIdInOrderByCreatedAtDesc(Collection<String> ids);
    List<ProductCommissionDeal> findByCollaboratorIdAndProductIdOrderByApprovedAtDesc(String collaboratorId, String productId);
    boolean existsByCollaboratorIdAndProductIdAndStatus(String collaboratorId, String productId, String status);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ProductCommissionDeal d where d.id = :id")
    Optional<ProductCommissionDeal> locked(@Param("id") String id);
}
