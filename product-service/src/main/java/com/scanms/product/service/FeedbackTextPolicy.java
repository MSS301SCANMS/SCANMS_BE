package com.scanms.product.service;

import com.scanms.product.exception.AppException;
import com.scanms.product.exception.ErrorCode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Component
public class FeedbackTextPolicy {
    private final List<Pattern> blocked;

    public FeedbackTextPolicy() throws IOException {
        var resource = new ClassPathResource("moderation/feedback-blocklist.txt");
        try (var stream = resource.getInputStream()) {
            blocked = new String(stream.readAllBytes(), StandardCharsets.UTF_8).lines()
                    .map(String::trim).filter(line -> !line.isEmpty() && !line.startsWith("#"))
                    .map(this::pattern).toList();
        }
    }

    public void validate(String comment) {
        if (comment == null || comment.isBlank()) return;
        String text = Normalizer.normalize(comment, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Cf}]", "");
        if (blocked.stream().anyMatch(pattern -> pattern.matcher(text).find()))
            throw new AppException(ErrorCode.INVALID_REQUEST, "Nhận xét có ngôn từ không phù hợp. Vui lòng chỉnh sửa trước khi đăng đánh giá.");
    }

    private Pattern pattern(String word) {
        var letters = Normalizer.normalize(word, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).codePoints()
                .filter(value -> !Character.isWhitespace(value)).mapToObj(value -> Pattern.quote(new String(Character.toChars(value)))).toList();
        // Keep Vietnamese accents: removing them would incorrectly block ordinary words such as "lớn".
        return Pattern.compile("(?<![\\p{L}\\p{N}])" + String.join("[\\p{P}\\p{Z}\\p{S}]*", letters) + "(?![\\p{L}\\p{N}])");
    }
}
