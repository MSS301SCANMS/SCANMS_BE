package com.scanms.promotion.service;

import com.scanms.promotion.dto.response.CollaboratorProfileResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface KolApplicationService {
    CollaboratorProfileResponse submit(Map<String, Object> input);

    static void validate(Map<String, Object> data) {
        if (data == null)
            throw invalid("Hồ sơ chưa có thông tin chi tiết. Vui lòng bổ sung trước khi duyệt.");
        for (String key : List.of("idCardNumber", "bankName", "bankAccountNumber", "bankAccountName", "frontCardUrl", "backCardUrl", "platform", "channelName", "channelUrl"))
            if (!(data.get(key) instanceof String value)
                    || value.isBlank()
                    || value.length() > 4096)
                throw invalid("Hồ sơ còn thiếu hoặc chưa hợp lệ: " + key);
        if (!data.get("idCardNumber").toString().matches("[0-9]{9,12}"))
            throw invalid("Số CCCD phải gồm 9 đến 12 chữ số.");
        if (!Set.of("TIKTOK", "FACEBOOK", "YOUTUBE", "INSTAGRAM", "SHOPEE_VIDEO", "LEMON8", "OTHER")
                .contains(data.get("platform")))
            throw invalid("Nền tảng kênh chưa hợp lệ.");
        if (!(data.get("followerCount")
                instanceof Number count)
                || !Double.isFinite(count.doubleValue())
                || count.doubleValue() < 0
                || count.doubleValue() != Math.floor(count.doubleValue())
                || count.doubleValue() > 1_000_000_000_000L)
            throw invalid("Số người theo dõi chưa hợp lệ.");

        for (String key : List.of("channelUrl", "frontCardUrl", "backCardUrl", "channelProofUrl")) {
            if (data.get(key) == null || data.get(key).toString().isBlank()) continue;
            try {
                var uri = URI.create(data.get(key).toString());
                if (!Set.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null)
                    throw new IllegalArgumentException();
            } catch (Exception ex) {
                throw invalid("Đường dẫn chưa hợp lệ: " + key);
            }
        }
        for (Object value : data.values())
            if (value instanceof String text && text.length() > 4096)
                throw invalid("Một trường thông tin vượt quá độ dài cho phép.");
    }

    private static ResponseStatusException invalid(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
