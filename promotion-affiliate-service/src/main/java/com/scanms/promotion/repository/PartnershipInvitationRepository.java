package com.scanms.promotion.repository;

import com.scanms.promotion.entity.PartnershipInvitation;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.*;

public interface PartnershipInvitationRepository extends JpaRepository<PartnershipInvitation,String> {
    List<PartnershipInvitation> findByStoreIdInOrderByCreatedAtDesc(Collection<String> ids);
    List<PartnershipInvitation> findByCollaboratorIdOrderByCreatedAtDesc(String id);
    boolean existsByStoreIdAndCollaboratorIdAndProductIdAndStatusIn(String storeId,String collaboratorId,String productId,Collection<String> statuses);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select i from PartnershipInvitation i where i.id=:id")
    Optional<PartnershipInvitation> lockById(@Param("id") String id);
}
