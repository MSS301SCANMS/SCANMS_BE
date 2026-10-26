package com.scanms.user.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.databind.json.JsonMapper;

/**
 * Chỉ gửi lên Kafka sau khi transaction lưu user đã commit, để consumer không bao giờ nhận event
 * của user chưa tồn tại. Lỗi Kafka không làm hỏng đăng ký: ví vẫn được tạo lười khi người dùng
 * mở ví (FinanceService.ensureWallet).
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class UserEventPublisher {
    private final KafkaTemplate<String, String> kafka;
    private final JsonMapper json;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onUserRegistered(UserRegisteredEvent event) {
        try {
            kafka.send(UserRegisteredEvent.TOPIC, event.userId(), json.writeValueAsString(event))
                    .whenComplete((result, error) -> {
                        if (error != null)
                            log.warn("Khong gui duoc UserRegisteredEvent userId={}", event.userId(), error);
                    });
        } catch (Exception e) {
            log.warn("Khong gui duoc UserRegisteredEvent userId={}", event.userId(), e);
        }
    }
}
