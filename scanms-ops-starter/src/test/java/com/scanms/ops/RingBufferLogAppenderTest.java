package com.scanms.ops;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RingBufferLogAppenderTest {
    private final LoggerContext context = new LoggerContext();
    private OpsProperties props;
    private RingBufferLogAppender appender;

    @BeforeEach
    void setUp() {
        props = new OpsProperties();
        props.setLogBufferSize(3);
        props.setErrorBufferSize(2);
        props.setMaxStackLines(3);
        appender = new RingBufferLogAppender(props);
        appender.setContext(context);
        appender.start();
    }

    private void log(Level level, String message, Throwable error) {
        var logger = context.getLogger("com.scanms.payment.service.impl.FinanceServiceImpl");
        appender.doAppend(new LoggingEvent(logger.getClass().getName(), logger, level, message, error, null));
    }

    @Test
    void keepsOnlyNewestLinesNewestFirst() {
        for (int i = 1; i <= 5; i++) log(Level.INFO, "dong " + i, null);
        var entries = appender.recent("ALL", 10);
        assertEquals(3, entries.size());
        assertEquals("dong 5", entries.get(0).message());
        assertEquals("dong 3", entries.get(2).message());
        assertEquals(3, appender.size());
        assertEquals("c.s.p.s.i.FinanceServiceImpl", entries.get(0).logger());
    }

    @Test
    void filtersByExactLevelAndRespectsLimit() {
        log(Level.INFO, "a", null);
        log(Level.WARN, "b", null);
        log(Level.ERROR, "c", null);
        assertEquals(1, appender.recent("warn", 10).size());
        assertEquals("b", appender.recent("WARN", 10).get(0).message());
        assertEquals(1, appender.recent(null, 1).size());
    }

    @Test
    void recordsErrorsWithTrimmedStackTraceAndMasksSecrets() {
        log(Level.ERROR, "that bai password=hunter2 cho a.b@x.com", new IllegalStateException("hong"));
        var errors = appender.recentErrors();
        assertEquals(1, errors.size());
        assertFalse(errors.get(0).message().contains("hunter2"));
        assertFalse(errors.get(0).message().contains("a.b@"));
        assertTrue(errors.get(0).stackTrace().startsWith("java.lang.IllegalStateException: hong"));
        assertTrue(errors.get(0).stackTrace().contains("dòng nữa"));
    }

    @Test
    void errorBufferIsBoundedAndPlainErrorsHaveNoStack() {
        log(Level.ERROR, "e1", null);
        log(Level.ERROR, "e2", null);
        log(Level.ERROR, "e3", null);
        var errors = appender.recentErrors();
        assertEquals(2, errors.size());
        assertEquals("e3", errors.get(0).message());
        assertNull(errors.get(0).stackTrace());
    }
}
