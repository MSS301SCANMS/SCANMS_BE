package com.scanms.promotion.service;

import com.scanms.promotion.entity.CommissionPolicy;
import com.scanms.promotion.entity.ProductCommissionDeal;
import org.springframework.security.oauth2.jwt.Jwt;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Interface nghiệp vụ quản lý và báo giá thỏa thuận hoa hồng trực tiếp giữa Shop và KOL/Affiliate.
 * Tuân thủ quy chuẩn Clean Architecture: tách biệt Interface và Implementation.
 */
public interface ProductCommissionDealService {

    record Rate(
            BigDecimal baseRate,
            int extraPoints,
            BigDecimal totalRate,
            String dealId,
            String policyId,
            BigDecimal currentPolicyRate
    ) {}

    List<ProductCommissionDeal> mine();

    List<ProductCommissionDeal> shop(Jwt jwt);

    Rate quote(String productId);

    ProductCommissionDeal propose(String productId, int points, String message);

    ProductCommissionDeal respond(String id, String decision, String response);

    ProductCommissionDeal cancel(String id);

    Rate rate(
            String collaboratorId,
            String productId,
            String storeId,
            Instant orderCreatedAt,
            CommissionPolicy fallback
    );
}
