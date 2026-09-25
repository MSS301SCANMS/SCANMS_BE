package com.scanms.payment.repository;

import com.scanms.payment.entity.WithdrawalRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WithdrawalRequestRepository extends JpaRepository<WithdrawalRequest, String> {}

