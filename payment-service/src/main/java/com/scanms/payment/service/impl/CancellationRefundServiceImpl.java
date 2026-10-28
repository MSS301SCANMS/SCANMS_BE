package com.scanms.payment.service.impl;

import com.scanms.payment.client.CancellationOrderClient;
import com.scanms.payment.constant.*;
import com.scanms.payment.exception.AppException;
import com.scanms.payment.exception.ErrorCode;
import com.scanms.payment.repository.PaymentRepository;
import com.scanms.payment.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional
public class CancellationRefundServiceImpl implements CancellationRefundService {
    private final CancellationOrderClient orders;
    private final PaymentRepository payments;
    private final PaymentAccess access;
    private final FinanceService finance;
    private final FinanceOutbox outbox;
    private final jakarta.persistence.EntityManager em;
    @Value("${clients.order.url:http://localhost:8083}")
    private String orderUrl;

    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> refund(String orderId) {
        if (!access.role("ORDER_INTERNAL")) access.trustedOnly();
        Map<String, Object> basis;
        try {
            var envelope = orders.basis(orderId, "Bearer " + access.token());
            if (envelope == null || !(envelope.get("result") instanceof Map<?, ?> result))
                throw new AppException(ErrorCode.CONFLICT);
            basis = (Map<String, Object>) result;
        } catch (feign.FeignException ex) {
            throw new AppException(ErrorCode.CONFLICT, "Chưa xác minh được điều kiện hoàn tiền hủy đơn.");
        }
        var captured = payments.findByOrderIdOrderByCreatedAtDesc(orderId).stream()
                .filter(payment -> payment.getStatus() == PaymentStatus.SUCCESS && "ORDER".equals(payment.getPurpose()))
                .filter(payment -> basis.get("paymentId") == null || basis.get("paymentId").equals(payment.getPaymentId())).findFirst();
        if (captured.isEmpty()) return Map.of("refundStatus", "NO_CAPTURED_PAYMENT");
        var payment = captured.get();
        em.refresh(payment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        long amount = FinanceService.number(basis, "amountVnd");
        if (!orderId.equals(basis.get("orderId")) || !Objects.equals(payment.getPayerId(), basis.get("customerId"))
                || !"VND".equals(basis.get("currency")) || !Objects.equals(payment.getAmountVnd(), amount) || payment.getVerifiedAt() == null)
            throw new AppException(ErrorCode.CONFLICT, "Cancellation refund identity or amount mismatch");
        String reference = payment.getResolutionReference();
        if ("REFUNDED".equals(payment.getOrderSyncStatus()) && (payment.getResolutionAmountVnd() == null || payment.getResolutionAmountVnd() < amount))
            throw new AppException(ErrorCode.CONFLICT, "Previous refund amount requires reconciliation");
        if (!"REFUNDED".equals(payment.getOrderSyncStatus())) {
            var wallet = finance.ensureWallet(WalletOwnerType.CUSTOMER, payment.getPayerId());
            var entry = finance.post(wallet.getWalletId(), WalletTransactionType.REFUND, WalletTransactionDirection.CREDIT,
                    amount, "ORDER_CANCELLATION", orderId, "cancelled-order-payment:" + payment.getPaymentId());
            reference = entry.transactionId();
            payment.setOrderSyncStatus("REFUNDED");
            payment.setResolutionReference(reference);
            payment.setResolutionAmountVnd(amount);
            payment.setResolutionReason("Hoàn tiền đơn bị hủy về ví SCANMS");
            payment.setResolvedBy("ORDER_CANCELLATION");
            payments.save(payment);
        }
        if (reference == null) throw new AppException(ErrorCode.CONFLICT, "Refund ledger reference is missing");
        outbox.enqueue("cancel-refund:" + payment.getPaymentId(), orderUrl + "/api/v1/finance/orders/" + orderId + "/cancellation-refunded",
                Map.of("paymentId", payment.getPaymentId(), "transactionId", reference, "amountVnd", amount));
        return Map.of("refundStatus", "REFUNDED", "refundReference", reference, "amountVnd", amount, "destination", "SCANMS_WALLET");
    }
}
