package com.scanms.payment.service;

import com.scanms.payment.entity.Payment;

import java.util.Map;

public interface PayosProvider {
    boolean available();

    boolean payoutAvailable();

    Map<String, Object> createLink(Payment p);

    Map<String, Object> paymentStatus(long code);

    Map<String, Object> cancel(long code);

    void verifyWebhook(Map<String, Object> payload, String sig);

    void verifyPayoutNotification(Map<String, Object> payload, String sig);

    Map<String, Object> transfer(String reference, long amount, String bankCode, String number);

    Map<String, Object> payoutStatus(String id);

    Map<String, Object> findPayout(String reference);
}
