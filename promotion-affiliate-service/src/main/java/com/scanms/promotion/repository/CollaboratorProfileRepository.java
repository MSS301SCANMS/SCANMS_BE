package com.scanms.promotion.repository;

import com.scanms.promotion.entity.CollaboratorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface CollaboratorProfileRepository extends JpaRepository<CollaboratorProfile, UUID> {}
