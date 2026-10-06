package com.scanms.payment.repository;

import com.scanms.payment.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
    java.util.Optional<BankAccount> findBySourceKycReference(String reference);
    java.util.Optional<BankAccount> findByReplacesBankId(String id);
    java.util.List<BankAccount> findByStoreId(String id);
    java.util.List<BankAccount> findByCollaboratorId(String id);
}
