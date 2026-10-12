package com.scanms.user.service;

public interface OtpService {
    /** Sinh mã OTP 6 số mới cho email, lưu tạm (TTL 5 phút), trả về mã vừa sinh. */
    String generate(String email);

    /** Kiểm tra mã OTP còn hiệu lực và đúng cho email hay không (không xoá). */
    boolean verify(String email, String otp);

    /** Kiểm tra mã OTP như verify(), nếu hợp lệ thì xoá luôn để không thể dùng lại. */
    boolean consume(String email, String otp);
}
