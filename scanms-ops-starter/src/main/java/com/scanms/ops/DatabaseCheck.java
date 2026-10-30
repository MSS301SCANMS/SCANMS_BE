package com.scanms.ops;

import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;

public class DatabaseCheck implements DependencyCheck {
    private final DataSource dataSource;

    public DatabaseCheck(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public String name() {
        return "PostgreSQL";
    }

    @Override
    public Result check() {
        long started = System.nanoTime();
        try (Connection connection = dataSource.getConnection()) {
            boolean valid = connection.isValid(2);
            long ms = (System.nanoTime() - started) / 1_000_000;
            String pool = poolDetail();
            return valid ? Result.up(ms, pool) : Result.down(ms, "Kết nối không hợp lệ");
        } catch (Exception e) {
            return Result.down((System.nanoTime() - started) / 1_000_000, "Không kết nối được: " + e.getClass().getSimpleName());
        }
    }

    private String poolDetail() {
        try {
            if (dataSource instanceof HikariDataSource hikari && hikari.getHikariPoolMXBean() != null) {
                var pool = hikari.getHikariPoolMXBean();
                return "Kết nối đang dùng " + pool.getActiveConnections() + "/" + hikari.getMaximumPoolSize()
                        + ", chờ " + pool.getThreadsAwaitingConnection();
            }
        } catch (LinkageError ignored) {
            // service không dùng HikariCP: bỏ qua phần chi tiết hồ kết nối
        }
        return null;
    }
}
