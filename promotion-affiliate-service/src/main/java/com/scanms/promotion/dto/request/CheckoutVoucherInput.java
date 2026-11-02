package com.scanms.promotion.dto.request;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

public record CheckoutVoucherInput(
        String code, String customerId, List<Map<String, Object>> lines,
        @JsonInclude(JsonInclude.Include.NON_NULL) String shippingCode,
        @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, Long> shippingFees) {
    public CheckoutVoucherInput(String code, String customerId, List<Map<String, Object>> lines, String shippingCode) {
        this(code, customerId, lines, shippingCode, null);
    }

    public CheckoutVoucherInput(String code, String customerId, List<Map<String, Object>> lines) {
        this(code, customerId, lines, null, null);
    }
}
