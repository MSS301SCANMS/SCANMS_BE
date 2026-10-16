package com.scanms.user.service.impl;

import com.scanms.user.service.MailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MailServiceImpl implements MailService {
    private final JavaMailSender mailSender;

    @Override
    public void sendOtpEmail(String toEmail, String otp) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(toEmail);
        message.setSubject("SCANMS - Mã xác thực khôi phục mật khẩu");
        message.setText(
                "Mã OTP của bạn là: " + otp + "\n\n" +
                "Mã có hiệu lực trong 5 phút. Nếu không phải bạn yêu cầu khôi phục mật khẩu, vui lòng bỏ qua email này."
        );
        mailSender.send(message);
    }
}
