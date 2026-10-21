package com.scanms.promotion.repository;

import com.scanms.promotion.entity.ReferralLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReferralLinkRepository extends JpaRepository<ReferralLink, String> {
    Optional<ReferralLink> findByToken(String token);
    List<ReferralLink> findByCollaboratorId(String collaboratorId);
    List<ReferralLink> findByProductIdIn(Collection<String> productIds);
    long countByCollaboratorIdAndProductId(String collaboratorId, String productId);
}
