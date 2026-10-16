package com.scanms.user.service;

public interface MailService {
    void sendOtpEmail(String toEmail, String otp);
}
