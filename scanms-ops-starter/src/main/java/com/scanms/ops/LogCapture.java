package com.scanms.ops;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import org.slf4j.ILoggerFactory;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;

/**
 * Gắn {@link RingBufferLogAppender} vào logger gốc của Logback khi service khởi động và gỡ ra khi dừng
 * (gỡ để các test nhiều context trong cùng JVM không bị gắn trùng). Dòng log phát ra trước thời điểm này
 * (các dòng đầu lúc khởi động) sẽ không có trong bộ đệm.
 */
public class LogCapture implements DisposableBean {
    private final RingBufferLogAppender appender;
    private final Logger root;

    public LogCapture(OpsProperties props) {
        this.appender = new RingBufferLogAppender(props);
        ILoggerFactory factory = LoggerFactory.getILoggerFactory();
        if (factory instanceof LoggerContext context) {
            appender.setContext(context);
            appender.setName("scanmsOpsRingBuffer");
            appender.start();
            root = context.getLogger(Logger.ROOT_LOGGER_NAME);
            root.addAppender(appender);
        } else {
            root = null;
        }
    }

    public RingBufferLogAppender appender() {
        return appender;
    }

    @Override
    public void destroy() {
        if (root != null) root.detachAppender(appender);
        appender.stop();
    }
}
