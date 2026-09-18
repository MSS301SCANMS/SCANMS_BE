package com.scanms.payment.dto.request;

import com.scanms.payment.constant.*;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record RegisterBankAccountRequest(
        UUID storeId,
        UUID collaboratorId,
        @NotBlank String bankCode,
        @NotBlank String holderName,
        @NotBlank String accountCiphertext,
        String maskedNumber,
        BankAccountVerificationStatus verificationStatus,
        Boolean active
) {}
