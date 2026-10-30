package com.scanms.ops;

/** Một kết nối phụ thuộc cần kiểm tra (DB, Redis, Kafka…). Không bao giờ ném lỗi ra ngoài. */
public interface DependencyCheck {
    record Result(String status, long latencyMs, String detail) {
        public static Result up(long latencyMs, String detail) {
            return new Result("UP", latencyMs, detail);
        }

        public static Result down(long latencyMs, String detail) {
            return new Result("DOWN", latencyMs, detail);
        }
    }

    String name();

    Result check();
}
