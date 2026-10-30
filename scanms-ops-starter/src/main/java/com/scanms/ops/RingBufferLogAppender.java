package com.scanms.ops;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.AppenderBase;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/** Giữ N dòng log và M lỗi ERROR gần nhất trong bộ nhớ. Mất khi tiến trình dừng. */
public class RingBufferLogAppender extends AppenderBase<ILoggingEvent> {
    public record Entry(long time, String level, String logger, String thread, String message) {
    }

    public record ErrorEntry(long time, String logger, String thread, String message, String stackTrace) {
    }

    private final int capacity;
    private final int errorCapacity;
    private final int maxStackLines;
    private final int maxStackChars;
    private final int maxMessageChars;
    private final Deque<Entry> entries = new ArrayDeque<>();
    private final Deque<ErrorEntry> errors = new ArrayDeque<>();

    public RingBufferLogAppender(OpsProperties props) {
        this.capacity = Math.max(1, props.getLogBufferSize());
        this.errorCapacity = Math.max(1, props.getErrorBufferSize());
        this.maxStackLines = Math.max(1, props.getMaxStackLines());
        this.maxStackChars = Math.max(64, props.getMaxStackChars());
        this.maxMessageChars = Math.max(64, props.getMaxMessageChars());
    }

    @Override
    protected void append(ILoggingEvent event) {
        String message = truncate(LogMasker.mask(event.getFormattedMessage()), maxMessageChars);
        String logger = shortLogger(event.getLoggerName());
        Entry entry = new Entry(event.getTimeStamp(), event.getLevel().toString(), logger, event.getThreadName(), message);
        boolean isError = event.getLevel().isGreaterOrEqual(Level.ERROR);
        ErrorEntry error = isError
                ? new ErrorEntry(event.getTimeStamp(), logger, event.getThreadName(), message, stack(event.getThrowableProxy()))
                : null;
        synchronized (this) {
            push(entries, entry, capacity);
            if (error != null) push(errors, error, errorCapacity);
        }
    }

    /** Mới nhất trước; {@code level} rỗng hoặc ALL là lấy mọi mức. */
    public synchronized List<Entry> recent(String level, int limit) {
        boolean all = level == null || level.isBlank() || "ALL".equalsIgnoreCase(level);
        List<Entry> out = new ArrayList<>();
        for (var it = entries.descendingIterator(); it.hasNext() && out.size() < limit; ) {
            Entry e = it.next();
            if (all || e.level().equalsIgnoreCase(level)) out.add(e);
        }
        return out;
    }

    public synchronized List<ErrorEntry> recentErrors() {
        List<ErrorEntry> out = new ArrayList<>(errors.size());
        errors.descendingIterator().forEachRemaining(out::add);
        return out;
    }

    public synchronized int size() {
        return entries.size();
    }

    public int capacity() {
        return capacity;
    }

    private static <T> void push(Deque<T> deque, T item, int max) {
        deque.addLast(item);
        while (deque.size() > max) deque.removeFirst();
    }

    private String stack(IThrowableProxy proxy) {
        if (proxy == null) return null;
        String[] lines = LogMasker.mask(ThrowableProxyUtil.asString(proxy)).split("\\R");
        StringBuilder sb = new StringBuilder();
        int count = Math.min(lines.length, maxStackLines);
        for (int i = 0; i < count; i++) sb.append(lines[i]).append('\n');
        if (lines.length > maxStackLines) sb.append("... ").append(lines.length - maxStackLines).append(" dòng nữa\n");
        return truncate(sb.toString().stripTrailing(), maxStackChars);
    }

    private static String truncate(String text, int max) {
        if (text == null || text.length() <= max) return text;
        return text.substring(0, max) + "…";
    }

    /** {@code com.scanms.payment.service.impl.FinanceServiceImpl} thành {@code c.s.p.s.i.FinanceServiceImpl}. */
    static String shortLogger(String name) {
        if (name == null) return "";
        String[] parts = name.split("\\.");
        if (parts.length <= 1) return name;
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length - 1; i++) sb.append(parts[i].isEmpty() ? "" : parts[i].charAt(0)).append('.');
        return sb.append(parts[parts.length - 1]).toString();
    }
}
