package com.scanms.payment.event;

import com.scanms.payment.constant.WalletOwnerType;
import com.scanms.payment.service.FinanceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tạo ví CUSTOMER khi user-service báo có tài khoản mới. Idempotent: ensureWallet tìm trước khi
 * tạo, và nếu hai nơi tạo cùng lúc (consumer và lần mở ví đầu tiên) thì unique constraint
 * uk_wallet_owner_currency chặn bản sao, ta chỉ cần đọc lại ví đã có.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserRegisteredConsumer {
    static final String TOPIC = "user.registered.v1";
    private final FinanceService finance;
    private final JsonMapper json;

    @KafkaListener(topics = TOPIC, groupId = "${spring.kafka.consumer.group-id:payment-service}")
    public void onUserRegistered(String payload) {
        JsonNode event = json.readTree(payload);
        String userId = event.path("userId").asString("");
        if (userId.isBlank()) {
            log.warn("Bo qua UserRegisteredEvent khong co userId: {}", payload);
            return;
        }
        try {
            finance.ensureWallet(WalletOwnerType.CUSTOMER, userId);
        } catch (DataIntegrityViolationException race) {
            finance.ensureWallet(WalletOwnerType.CUSTOMER, userId);
        }
        log.info("Da dam bao vi CUSTOMER cho userId={}", userId);
    }
}
