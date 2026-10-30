package com.scanms.payment.service;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Số liệu vận hành của Payment cho trang Giám sát hệ thống: cờ cấu hình (chỉ đúng/sai, không bao giờ trả khóa
 * hay mật khẩu) và số lượng bản ghi theo trạng thái. Không trả số dư hay thông tin của từng người dùng.
 */
@Service
public class PaymentOverviewService {
    @PersistenceContext
    private EntityManager em;
    private final PayosProvider provider;
    private final FinanceOutbox outbox;
    @Value("${scanms.payment.sandbox-enabled:false}")
    private boolean sandboxEnabled;
    @Value("${scanms.payment.automatic-payouts:true}")
    private boolean automaticPayouts;
    @Value("${scanms.payment.payout-delay-ms:60000}")
    private long payoutDelayMs;
    @Value("${scanms.payment.minimum-withdrawal-vnd:200000}")
    private long minimumWithdrawalVnd;
    @Value("${scanms.payment.bank-encryption-key:}")
    private String bankEncryptionKey;
    @Value("${scanms.payment.service-token-url:}")
    private String serviceTokenUrl;

    public PaymentOverviewService(PayosProvider provider, FinanceOutbox outbox) {
        this.provider = provider;
        this.outbox = outbox;
    }

    @Transactional(readOnly = true)
    public Map<String, Object> overview() {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("sandboxEnabled", sandboxEnabled);
        config.put("automaticPayouts", automaticPayouts);
        config.put("payoutDelayMs", payoutDelayMs);
        config.put("minimumWithdrawalVnd", minimumWithdrawalVnd);
        config.put("paymentProviderConfigured", provider.available());
        config.put("payoutProviderConfigured", provider.payoutAvailable());
        config.put("bankEncryptionKeyConfigured", bankEncryptionKey != null && !bankEncryptionKey.isBlank());
        config.put("serviceTokenConfigured", serviceTokenUrl != null && !serviceTokenUrl.isBlank());
        config.put("financeOutboxConfigured", outbox.configured());

        Map<String, Object> events = new LinkedHashMap<>();
        events.put("pending", count("select count(e) from FinanceEvent e where e.deliveredAt is null"));
        events.put("blocked", count("select count(e) from FinanceEvent e where e.deliveredAt is null and e.blocked = true"));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("config", config);
        body.put("financeEvents", events);
        body.put("paymentsByStatus", grouped("select p.status, count(p) from Payment p group by p.status"));
        body.put("withdrawalsByStatus", grouped("select w.status, count(w) from WithdrawalRequest w group by w.status"));
        body.put("walletsByOwnerType", grouped("select w.ownerType, count(w) from Wallet w group by w.ownerType"));
        return body;
    }

    private long count(String jpql) {
        return em.createQuery(jpql, Long.class).getSingleResult();
    }

    private List<Map<String, Object>> grouped(String jpql) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Object[] row : em.createQuery(jpql, Object[].class).getResultList()) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("key", String.valueOf(row[0]));
            item.put("count", row[1]);
            rows.add(item);
        }
        return rows;
    }
}
