package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;

public record CreateWalletRequest(
        @NotNull WalletOwnerType ownerType,
        @NotNull String ownerRefId,
        @NotBlank String currency,
        @NotNull @PositiveOrZero Long availableBalanceVnd,
        @NotNull @PositiveOrZero Long heldBalanceVnd,
        @NotNull WalletStatus status
) {}

