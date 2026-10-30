package com.scanms.promotion.service;

import com.scanms.promotion.dto.request.*;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Map;

public interface FinanceContractService {
    Map<String, Object> basis(String id, Jwt jwt);

    Map<String, Object> credited(String id, CreditInput input, Jwt jwt);

    Map<String, Object> total(String orderItemIds, Jwt jwt);

    Map<String, Object> initializeCommission(String itemId, InitInput input, Jwt jwt);

    Map<String, Object> adjustCommission(String itemId, AdjustInput input, Jwt jwt);

    Map<String, Object> finalizeItem(String itemId, FinalizeInput input, Jwt jwt);
}
