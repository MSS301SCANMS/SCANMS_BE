package com.scanms.ops;

import java.util.regex.Pattern;

/**
 * Che dữ liệu nhạy cảm trước khi đưa log ra trang giám sát. Chỉ là lớp bảo vệ bổ sung dựa trên mẫu,
 * không đảm bảo tuyệt đối, nên log vẫn chỉ dành cho quản trị viên.
 */
public final class LogMasker {
    private static final Pattern JWT = Pattern.compile("eyJ[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}\\.[A-Za-z0-9_-]{5,}");
    private static final Pattern BEARER = Pattern.compile("(?i)((?:bearer|basic)\\s+)[A-Za-z0-9._~+/=-]{8,}");
    private static final Pattern SECRET = Pattern.compile(
            "(?i)((?:pass(?:word)?|pwd|secret|token|api[-_]?key|checksum[-_]?key)\\s*[=:]\\s*)\"?[^\\s\",;&]+\"?");
    private static final Pattern EMAIL = Pattern.compile("([A-Za-z0-9._%+-])[A-Za-z0-9._%+-]*@([A-Za-z0-9.-]+\\.[A-Za-z]{2,})");

    private LogMasker() {
    }

    public static String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String out = JWT.matcher(text).replaceAll("[token]");
        out = BEARER.matcher(out).replaceAll("$1[token]");
        out = SECRET.matcher(out).replaceAll("$1***");
        return EMAIL.matcher(out).replaceAll("$1***@$2");
    }
}
