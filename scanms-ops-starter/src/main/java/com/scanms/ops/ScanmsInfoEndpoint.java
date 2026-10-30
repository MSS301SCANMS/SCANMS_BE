package com.scanms.ops;

import org.springframework.boot.SpringBootVersion;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.core.env.Environment;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.net.InetAddress;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** {@code /actuator/scanmsinfo}: thông tin tiến trình và các kết nối phụ thuộc của service. */
@Endpoint(id = "scanmsinfo")
public class ScanmsInfoEndpoint {
    private final OpsProperties props;
    private final Environment env;
    private final OpsAccess access;
    private final List<DependencyCheck> checks;

    public ScanmsInfoEndpoint(OpsProperties props, Environment env, OpsAccess access, List<DependencyCheck> checks) {
        this.props = props;
        this.env = env;
        this.access = access;
        this.checks = checks;
    }

    @ReadOperation
    public WebEndpointResponse<Map<String, Object>> info() {
        WebEndpointResponse<Map<String, Object>> denied = OpsResponses.denied(access.check());
        if (denied != null) return denied;

        var runtime = ManagementFactory.getRuntimeMXBean();
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        var threads = ManagementFactory.getThreadMXBean();

        Map<String, Object> process = new LinkedHashMap<>();
        process.put("pid", ProcessHandle.current().pid());
        process.put("host", hostName());
        process.put("javaVersion", System.getProperty("java.version"));
        process.put("javaVendor", System.getProperty("java.vendor"));
        process.put("springBootVersion", SpringBootVersion.getVersion());
        process.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        process.put("startedAt", Instant.ofEpochMilli(runtime.getStartTime()).toString());
        process.put("uptimeMs", runtime.getUptime());

        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("heapUsedBytes", memory.getHeapMemoryUsage().getUsed());
        mem.put("heapMaxBytes", memory.getHeapMemoryUsage().getMax());
        mem.put("nonHeapUsedBytes", memory.getNonHeapMemoryUsage().getUsed());

        List<Map<String, Object>> dependencies = new ArrayList<>();
        for (DependencyCheck check : checks) {
            DependencyCheck.Result result = check.check();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", check.name());
            row.put("status", result.status());
            row.put("latencyMs", result.latencyMs());
            row.put("detail", result.detail());
            dependencies.add(row);
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("service", props.getServiceName() != null ? props.getServiceName() : env.getProperty("spring.application.name", "unknown"));
        body.put("port", port());
        body.put("process", process);
        body.put("memory", mem);
        body.put("threads", Map.of("live", threads.getThreadCount(), "peak", threads.getPeakThreadCount()));
        body.put("profiles", List.of(env.getActiveProfiles()));
        body.put("dependencies", dependencies);
        body.put("generatedAt", Instant.now().toString());
        return new WebEndpointResponse<>(body, 200);
    }

    private Integer port() {
        String value = env.getProperty("local.server.port", env.getProperty("server.port"));
        try {
            return value == null ? null : Integer.valueOf(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String hostName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return null;
        }
    }
}
