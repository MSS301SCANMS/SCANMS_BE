package com.scanms.livestream.repository;

import com.scanms.livestream.entity.Livestream;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface LivestreamRepository extends JpaRepository<Livestream, UUID> {}
