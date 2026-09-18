package com.scanms.promotion.repository;

import com.scanms.promotion.entity.ReferralLink;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface ReferralLinkRepository extends JpaRepository<ReferralLink, UUID> {}
