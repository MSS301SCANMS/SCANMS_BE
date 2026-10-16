package com.scanms.user.service.impl;

import com.scanms.user.service.OtpService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sinh và lưu tạm mã OTP (6 số) trong bộ nhớ, dùng cho luồng quên mật khẩu.
 * Lưu ý: đây là in-memory store đơn giản cho môi trường 1 instance (dev/demo).
 * Nếu scale nhiều instance sau này, nên chuyển sang Redis.
 */
@Service
public class OtpServiceImpl implements OtpService {
    private static final long OTP_TTL_SECONDS = 5 * 60; // 5 phút
    private static final SecureRandom RANDOM = new SecureRandom();

    private record OtpEntry(String code, Instant expiresAt) {}

    private final Map<String, OtpEntry> store = new ConcurrentHashMap<>();

    @Override
    public String generate(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        store.put(normalize(email), new OtpEntry(code, Instant.now().plusSeconds(OTP_TTL_SECONDS)));
        return code;
    }

    @Override
    public boolean verify(String email, String otp) {
        OtpEntry entry = store.get(normalize(email));
        if (entry == null) {
            return false;
        }
        if (Instant.now().isAfter(entry.expiresAt())) {
            store.remove(normalize(email));
            return false;
        }
        return entry.code().equals(otp);
    }

    @Override
    public boolean consume(String email, String otp) {
        boolean valid = verify(email, otp);
        if (valid) {
            store.remove(normalize(email));
        }
        return valid;
    }

    private String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase();
    }
}
