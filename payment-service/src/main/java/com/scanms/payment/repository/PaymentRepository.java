package com.scanms.payment.repository;

import com.scanms.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import com.scanms.payment.constant.PaymentStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    Page<Payment> findByPayerId(String payerId,Pageable pageable);
    boolean existsByWalletIdAndStatusIn(String walletId,Collection<PaymentStatus> statuses);
    Optional<Payment> findByIdempotencyKey(String key);
    Optional<Payment> findByProviderOrderCode(Long code);
    List<Payment> findByOrderIdOrderByCreatedAtDesc(String id);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Payment p where p.paymentId = :id")
    Optional<Payment> lockById(@Param("id") String id);
}
