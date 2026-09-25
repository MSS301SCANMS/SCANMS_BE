package com.scanms.product.repository;

import com.scanms.product.entity.Livestream;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LivestreamRepository extends JpaRepository<Livestream, String> {}
