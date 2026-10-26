package com.scanms.payment.service;

import java.util.Map;
import java.util.Optional;

public interface FinanceOutbox {
    boolean configured();

    Map<String, Object> pending(int page);

    void retry(String id);

    void enqueue(
            String key,
            String destination,
            Map<String, Object> payload
    );

    Optional<String> serviceToken();

    void dispatch();
}
