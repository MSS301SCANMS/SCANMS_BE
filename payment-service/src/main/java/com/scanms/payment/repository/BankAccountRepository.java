package com.scanms.payment.repository;

import com.scanms.payment.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;

public interface BankAccountRepository extends JpaRepository<BankAccount, UUID> {}
