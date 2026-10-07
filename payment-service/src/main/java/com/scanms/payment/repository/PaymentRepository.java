package com.scanms.payment.repository;

import com.scanms.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PaymentRepository extends JpaRepository<Payment, String> {
    org.springframework.data.domain.Page<Payment> findByPayerId(String payerId,org.springframework.data.domain.Pageable pageable);
    boolean existsByWalletIdAndStatusIn(String walletId,java.util.Collection<com.scanms.payment.constant.PaymentStatus> statuses);
    java.util.Optional<Payment> findByIdempotencyKey(String key);
    java.util.Optional<Payment> findByProviderOrderCode(Long code);
    java.util.List<Payment> findByOrderIdOrderByCreatedAtDesc(String id);
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select p from Payment p where p.paymentId = :id")
    java.util.Optional<Payment> lockById(@org.springframework.data.repository.query.Param("id") String id);
}
