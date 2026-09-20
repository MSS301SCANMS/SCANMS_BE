package com.scanms.catalog.repository;

import com.scanms.catalog.entity.Livestream;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivestreamRepository extends JpaRepository<Livestream, UUID> {}
