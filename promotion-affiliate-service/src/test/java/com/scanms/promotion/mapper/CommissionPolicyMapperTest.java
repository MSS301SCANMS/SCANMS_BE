package com.scanms.promotion.mapper;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;

class CommissionPolicyMapperTest {
    @Test
    void mapsScopeAndRateSnapshotSource() {
        String productId = java.util.UUID.randomUUID().toString();
        var request = new CreateCommissionPolicyRequest(
                "Product policy", CommissionPolicyScope.PRODUCT, null, productId,
                null, null, new BigDecimal("0.10"), 14, Instant.now(), null,
                CommissionPolicyStatus.ACTIVE, 10);
        var entity = new CommissionPolicyMapper().toEntity(request);
        assertEquals(productId, entity.getProductId());
        assertEquals(new BigDecimal("0.10"), entity.getCommissionRate());
        assertEquals(14, entity.getReturnWindowDays());
    }
}

