package com.scanms.ops;

import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.web.WebEndpointResponse;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpsEndpointsTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(OpsAutoConfiguration.class))
            .withPropertyValues("spring.application.name=demo-service", "server.port=8099");

    @Test
    void infoReportsProcessMemoryAndServiceName() {
        runner.run(context -> {
            WebEndpointResponse<Map<String, Object>> response = context.getBean(ScanmsInfoEndpoint.class).info();
            assertEquals(200, response.getStatus());
            Map<String, Object> body = response.getBody();
            assertEquals("demo-service", body.get("service"));
            assertEquals(8099, body.get("port"));
            Map<?, ?> process = (Map<?, ?>) body.get("process");
            assertEquals(ProcessHandle.current().pid(), process.get("pid"));
            assertNotNull(process.get("javaVersion"));
            assertNotNull(process.get("springBootVersion"));
            assertTrue((Long) ((Map<?, ?>) body.get("memory")).get("heapUsedBytes") > 0);
            assertTrue(((List<?>) body.get("dependencies")).isEmpty());
        });
    }

    @Test
    void customDependencyChecksAreReported() {
        runner.withBean(DependencyCheck.class, () -> new DependencyCheck() {
            public String name() { return "Fake"; }
            public Result check() { return Result.up(3, "ok"); }
        }).run(context -> {
            List<?> deps = (List<?>) context.getBean(ScanmsInfoEndpoint.class).info().getBody().get("dependencies");
            assertEquals(1, deps.size());
            Map<?, ?> row = (Map<?, ?>) deps.get(0);
            assertEquals("Fake", row.get("name"));
            assertEquals("UP", row.get("status"));
        });
    }

    @Test
    void logsCaptureWhatTheServiceLogsAndFilterByLevel() {
        runner.run(context -> {
            var log = LoggerFactory.getLogger("com.scanms.demo.Thing");
            log.info("dang xu ly don hang");
            log.error("loi thanh toan", new IllegalStateException("payos tu choi"));
            var endpoint = context.getBean(ScanmsLogsEndpoint.class);

            Map<String, Object> all = endpoint.logs(null, null).getBody();
            assertEquals("ALL", all.get("level"));
            assertTrue(((List<?>) all.get("entries")).size() >= 2);

            Map<String, Object> onlyErrors = endpoint.logs("error", 5).getBody();
            List<?> entries = (List<?>) onlyErrors.get("entries");
            assertFalse(entries.isEmpty());
            assertTrue(entries.stream().allMatch(e -> "ERROR".equals(((RingBufferLogAppender.Entry) e).level())));
            List<?> errors = (List<?>) onlyErrors.get("errors");
            assertTrue(((RingBufferLogAppender.ErrorEntry) errors.get(0)).stackTrace().contains("payos tu choi"));
        });
    }

    @Test
    void invalidLevelIsABadRequest() {
        runner.run(context -> assertEquals(400, context.getBean(ScanmsLogsEndpoint.class).logs("LOUD", null).getStatus()));
    }

    @Test
    void deniedCallersGetNoData() {
        runner.withBean(OpsAccess.class, () -> () -> OpsAccess.Decision.FORBIDDEN).run(context -> {
            assertEquals(403, context.getBean(ScanmsInfoEndpoint.class).info().getStatus());
            assertEquals(403, context.getBean(ScanmsLogsEndpoint.class).logs(null, null).getStatus());
        });
        runner.withBean(OpsAccess.class, () -> () -> OpsAccess.Decision.UNAUTHENTICATED).run(context ->
                assertEquals(401, context.getBean(ScanmsInfoEndpoint.class).info().getStatus()));
    }

    @Test
    void canBeSwitchedOff() {
        runner.withPropertyValues("scanms.ops.enabled=false").run(context ->
                assertFalse(context.containsBean("scanmsInfoEndpoint")));
    }

    @Test
    void appenderIsDetachedWhenTheContextCloses() {
        var root = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(org.slf4j.Logger.ROOT_LOGGER_NAME);
        long before = java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(root.iteratorForAppenders(), 0), false).count();
        runner.run(context -> { });
        long after = java.util.stream.StreamSupport.stream(
                java.util.Spliterators.spliteratorUnknownSize(root.iteratorForAppenders(), 0), false).count();
        assertEquals(before, after);
    }
}
