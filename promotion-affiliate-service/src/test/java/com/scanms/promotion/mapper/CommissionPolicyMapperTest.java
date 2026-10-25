package com.scanms.promotion.mapper;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.dto.request.CreateCommissionPolicyRequest;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.time.Instant;
import static org.junit.jupiter.api.Assertions.*;
import org.mapstruct.factory.Mappers;

class CommissionPolicyMapperTest {
    @Test
    void mapsScopeAndRateSnapshotSource() {
        String productId = java.util.UUID.randomUUID().toString();
        var request = new CreateCommissionPolicyRequest(
                "Product policy", CommissionPolicyScope.PRODUCT, null, productId,
                null, null, new BigDecimal("10"), 14, Instant.now(), null,
                CommissionPolicyStatus.ACTIVE, 10);
        var entity = Mappers.getMapper(CommissionPolicyMapper.class).toEntity(request);
        assertEquals(productId, entity.getProductId());
        // Rate là phần trăm (10 = 10%) — finalize chia /100
        assertEquals(new BigDecimal("10"), entity.getCommissionRate());
        assertEquals(14, entity.getReturnWindowDays());
    }
}

