package com.scanms.order.service;

import com.scanms.order.dto.response.LivestreamSalesResponse;
import com.scanms.order.dto.response.StoreSalesResponse;
import org.springframework.security.oauth2.jwt.Jwt;

public interface LivestreamSalesService {
    LivestreamSalesResponse summary(
            String livestreamId,
            Jwt jwt
    );

    StoreSalesResponse storeSummary(
            String storeId,
            Jwt jwt
    );
}
