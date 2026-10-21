package com.scanms.promotion.repository;

import com.scanms.promotion.entity.ReferralLink;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReferralLinkRepository extends JpaRepository<ReferralLink, String> {
    java.util.Optional<ReferralLink> findByToken(String token);
    java.util.List<ReferralLink> findByCollaboratorId(String collaboratorId);
    java.util.List<ReferralLink> findByProductIdIn(java.util.Collection<String> productIds);
    long countByCollaboratorIdAndProductId(String collaboratorId, String productId);
}
