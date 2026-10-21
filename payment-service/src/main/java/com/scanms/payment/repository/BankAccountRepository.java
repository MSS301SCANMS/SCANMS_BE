package com.scanms.payment.repository;

import com.scanms.payment.entity.BankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface BankAccountRepository extends JpaRepository<BankAccount, String> {
    Optional<BankAccount> findBySourceKycReference(String reference);
    Optional<BankAccount> findByReplacesBankId(String id);
    List<BankAccount> findByStoreId(String id);
    List<BankAccount> findByCollaboratorId(String id);
}
