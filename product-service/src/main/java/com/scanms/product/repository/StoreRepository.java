package com.scanms.product.repository;

import com.scanms.product.entity.Store;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoreRepository extends JpaRepository<Store, String> {}
