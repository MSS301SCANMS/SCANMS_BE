package com.scanms.promotion.dto.request;

import java.util.List;
import java.util.Map;

public record CheckoutVoucherInput(String code, String customerId, List<Map<String, Object>> lines) {}
