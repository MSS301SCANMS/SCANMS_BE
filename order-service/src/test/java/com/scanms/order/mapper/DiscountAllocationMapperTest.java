package com.scanms.order.mapper;

import com.scanms.order.constant.*;
import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DiscountAllocationMapperTest {
    @Test
    void mapsMoneyAndLogicalReferences() {
        String orderItemId = java.util.UUID.randomUUID().toString();
        String voucherId = java.util.UUID.randomUUID().toString();
        var request = new CreateDiscountAllocationRequest(
                orderItemId, voucherId, DiscountSourceType.VOUCHER,
                DiscountFundingType.PLATFORM, 20_000L, "{\"rule\":\"WELCOME\"}");
        var entity = new DiscountAllocationMapper().toEntity(request);
        assertEquals(orderItemId, entity.getOrderItemId());
        assertEquals(voucherId, entity.getVoucherId());
        assertEquals(20_000L, entity.getAllocatedAmountVnd());
    }
}

