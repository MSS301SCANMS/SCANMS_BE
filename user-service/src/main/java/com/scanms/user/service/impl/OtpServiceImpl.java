package com.scanms.user.service.impl;

import com.scanms.user.exception.AppException;
import com.scanms.user.exception.ErrorCode;
import com.scanms.user.service.OtpService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.function.Supplier;

/**
 * Mã OTP (6 số) cho luồng quên mật khẩu, lưu trong Redis để mọi instance user-service dùng chung.
 * <ul>
 *   <li>{@code scanms:user:otp:code:<sha256(email)>} — mã OTP, tự hết hạn sau {@link #OTP_TTL} (Redis TTL).</li>
 *   <li>{@code scanms:user:otp:attempts:<sha256(email)>} — số lần nhập sai; đủ {@link #MAX_FAILED_ATTEMPTS} thì huỷ mã.</li>
 * </ul>
 * Email được băm để không lộ địa chỉ trong key Redis. {@link #consume} dùng Lua script nên
 * "kiểm tra + xoá" là nguyên tử: một mã chỉ dùng được đúng một lần dù có nhiều request song song.
 */
@Service
@RequiredArgsConstructor
public class OtpServiceImpl implements OtpService {
    static final Duration OTP_TTL = Duration.ofMinutes(5);
    static final int MAX_FAILED_ATTEMPTS = 5;

    private static final String CODE_PREFIX = "scanms:user:otp:code:";
    private static final String ATTEMPTS_PREFIX = "scanms:user:otp:attempts:";
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Xoá mã + bộ đếm nếu mã khớp; trả 1 khi thành công, 0 khi sai/hết hạn.
     */
    private static final DefaultRedisScript<Long> CONSUME_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('GET', KEYS[1]) == ARGV[1] then "
                    + "redis.call('DEL', KEYS[1], KEYS[2]) return 1 end "
                    + "return 0",
            Long.class);

    private final StringRedisTemplate redis;

    @Override
    public String generate(String email) {
        String code = String.format("%06d", RANDOM.nextInt(1_000_000));
        String id = emailId(email);
        redisCall(() -> {
            redis.opsForValue().set(CODE_PREFIX + id, code, OTP_TTL);
            redis.delete(ATTEMPTS_PREFIX + id);
            return null;
        });
        return code;
    }

    @Override
    public boolean verify(String email, String otp) {
        if (isBlank(otp)) return false;
        String id = emailId(email);
        String stored = redisCall(() -> redis.opsForValue().get(CODE_PREFIX + id));
        if (stored == null) return false;
        if (MessageDigest.isEqual(stored.getBytes(StandardCharsets.UTF_8), otp.trim().getBytes(StandardCharsets.UTF_8))) {
            return true;
        }
        registerFailedAttempt(id);
        return false;
    }

    @Override
    public boolean consume(String email, String otp) {
        if (isBlank(otp)) return false;
        String id = emailId(email);
        Long result = redisCall(() -> redis.execute(
                CONSUME_SCRIPT, List.of(CODE_PREFIX + id, ATTEMPTS_PREFIX + id), otp.trim()));
        if (result != null && result == 1L) return true;
        registerFailedAttempt(id);
        return false;
    }

    /**
     * Đếm lần nhập sai trong thời hạn của mã; quá giới hạn thì huỷ mã để chống dò 6 chữ số.
     */
    private void registerFailedAttempt(String id) {
        redisCall(() -> {
            String attemptsKey = ATTEMPTS_PREFIX + id;
            Long attempts = redis.opsForValue().increment(attemptsKey);
            if (attempts != null && attempts == 1L) redis.expire(attemptsKey, OTP_TTL);
            if (attempts != null && attempts >= MAX_FAILED_ATTEMPTS) {
                redis.delete(List.of(CODE_PREFIX + id, attemptsKey));
            }
            return null;
        });
    }

    private <T> T redisCall(Supplier<T> action) {
        try {
            return action.get();
        } catch (DataAccessException ex) {
            throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR,
                    "Dịch vụ OTP tạm thời không khả dụng, vui lòng thử lại sau");
        }
    }

    private static String emailId(String email) {
        String normalized = email == null ? "" : email.trim().toLowerCase();
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
