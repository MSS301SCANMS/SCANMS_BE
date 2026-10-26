package com.scanms.payment.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KafkaHealthServiceTest {
    private KafkaHealthService service(String bootstrap) {
        var s = new KafkaHealthService(new KafkaAdmin(Map.of("bootstrap.servers", bootstrap)));
        ReflectionTestUtils.setField(s, "groupId", "payment-service");
        return s;
    }

    @Test
    void reportsDownInsteadOfThrowingWhenBrokerIsUnreachable() {
        var status = service("localhost:1").status();
        assertEquals(false, status.get("up"));
        assertNotNull(status.get("message"));
    }

    /** Chạy tay với broker thật: mvn test -Dkafka.live=localhost:9092 -Dtest=KafkaHealthServiceTest */
    @Test
    @EnabledIfSystemProperty(named = "kafka.live", matches = ".+")
    void reportsClusterWhenBrokerIsRunning() {
        var status = service(System.getProperty("kafka.live")).status();
        System.out.println("KAFKA STATUS: " + status);
        assertEquals(true, status.get("up"));
        assertNotNull(status.get("clusterId"));
    }
}
