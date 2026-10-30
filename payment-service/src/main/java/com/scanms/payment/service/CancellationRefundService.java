package com.scanms.payment.service;

import java.util.Map;

public interface CancellationRefundService {
    Map<String, Object> refund(String orderId);
}
