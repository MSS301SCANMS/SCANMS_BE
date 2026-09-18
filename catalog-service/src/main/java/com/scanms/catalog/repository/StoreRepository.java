package com.scanms.catalog.repository;

import com.scanms.catalog.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface StoreRepository extends JpaRepository<Store, UUID> {}
