package com.scanms.ops;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

public class RedisCheck implements DependencyCheck {
    private final RedisConnectionFactory factory;

    public RedisCheck(RedisConnectionFactory factory) {
        this.factory = factory;
    }

    @Override
    public String name() {
        return "Redis";
    }

    @Override
    public Result check() {
        long started = System.nanoTime();
        try (RedisConnection connection = factory.getConnection()) {
            String reply = connection.ping();
            long ms = (System.nanoTime() - started) / 1_000_000;
            return "PONG".equalsIgnoreCase(reply) ? Result.up(ms, null) : Result.down(ms, "Phản hồi lạ: " + reply);
        } catch (Exception e) {
            return Result.down((System.nanoTime() - started) / 1_000_000, "Không kết nối được: " + e.getClass().getSimpleName());
        }
    }
}
