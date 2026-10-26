package com.scanms.order.dto.request;

public record PaidInput(String paymentId, Long amountVnd, String currency, String verifiedAt) {}
