package com.scanms.ops;

import org.jspecify.annotations.Nullable;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.core.env.Environment;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** {@code /actuator/scanmslogs?level=&limit=}: log gần nhất và các lỗi ERROR kèm stack trace. */
@Endpoint(id = "scanmslogs")
public class ScanmsLogsEndpoint {
    private static final Set<String> LEVELS = Set.of("ALL", "TRACE", "DEBUG", "INFO", "WARN", "ERROR");
    private final OpsProperties props;
    private final Environment env;
    private final OpsAccess access;
    private final LogCapture capture;

    public ScanmsLogsEndpoint(OpsProperties props, Environment env, OpsAccess access, LogCapture capture) {
        this.props = props;
        this.env = env;
        this.access = access;
        this.capture = capture;
    }

    @ReadOperation
    public WebEndpointResponse<Map<String, Object>> logs(@Nullable String level, @Nullable Integer limit) {
        WebEndpointResponse<Map<String, Object>> denied = OpsResponses.denied(access.check());
        if (denied != null) return denied;

        RingBufferLogAppender appender = capture.appender();
        String wanted = level == null || level.isBlank() ? "ALL" : level.trim().toUpperCase();
        if (!LEVELS.contains(wanted)) {
            return new WebEndpointResponse<>(Map.of("message", "Mức log không hợp lệ: " + level), 400);
        }
        int max = limit == null ? appender.capacity() : Math.max(1, Math.min(limit, appender.capacity()));

        List<RingBufferLogAppender.Entry> entries = appender.recent(wanted, max);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", props.getServiceName() != null ? props.getServiceName() : env.getProperty("spring.application.name", "unknown"));
        body.put("capacity", appender.capacity());
        body.put("size", appender.size());
        body.put("level", wanted);
        body.put("entries", entries);
        body.put("errors", appender.recentErrors());
        body.put("note", "Log nằm trong bộ nhớ, mất khi service khởi động lại.");
        return new WebEndpointResponse<>(body, 200);
    }
}
