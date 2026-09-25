package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.time.Instant;

public record WalletResponse(
        String walletId,
        WalletOwnerType ownerType,
        String ownerRefId,
        String currency,
        Long availableBalanceVnd,
        Long heldBalanceVnd,
        WalletStatus status,
        Long version,
        Instant createdAt,
        Instant updatedAt
) {}

