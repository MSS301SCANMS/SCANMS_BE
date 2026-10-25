package com.scanms.payment.mapper;

import com.scanms.payment.constant.*;
import com.scanms.payment.dto.request.CreateWalletRequest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import org.mapstruct.factory.Mappers;

class WalletMapperTest {
    @Test
    void mapsOwnerAndBalances() {
        String ownerId = java.util.UUID.randomUUID().toString();
        var request = new CreateWalletRequest(
                WalletOwnerType.STORE, ownerId, "VND", 50_000L, 10_000L, WalletStatus.ACTIVE);
        var entity = Mappers.getMapper(WalletMapper.class).toEntity(request);
        assertEquals(ownerId, entity.getOwnerRefId());
        assertEquals(50_000L, entity.getAvailableBalanceVnd());
        assertEquals(10_000L, entity.getHeldBalanceVnd());
    }
}

