package com.scanms.ops;

import org.apache.kafka.clients.admin.Admin;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class KafkaCheck implements DependencyCheck {
    private static final int TIMEOUT_MS = 2000;
    private final KafkaAdmin admin;

    public KafkaCheck(KafkaAdmin admin) {
        this.admin = admin;
    }

    @Override
    public String name() {
        return "Kafka";
    }

    @Override
    public Result check() {
        long started = System.nanoTime();
        Map<String, Object> props = new HashMap<>(admin.getConfigurationProperties());
        props.put("request.timeout.ms", TIMEOUT_MS);
        props.put("default.api.timeout.ms", TIMEOUT_MS);
        try (Admin client = Admin.create(props)) {
            int brokers = client.describeCluster().nodes().get(TIMEOUT_MS, TimeUnit.MILLISECONDS).size();
            return Result.up((System.nanoTime() - started) / 1_000_000, brokers + " broker");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Result.down((System.nanoTime() - started) / 1_000_000, "Bị gián đoạn");
        } catch (Exception e) {
            return Result.down((System.nanoTime() - started) / 1_000_000, "Không kết nối được broker");
        }
    }
}
