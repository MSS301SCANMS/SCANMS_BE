package com.scanms.payment.dto.response;

import com.scanms.payment.constant.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

public record BankAccountResponse(
        String bankAccountId,
        String storeId,
        String collaboratorId,
        String bankCode,
        String holderName,
        String accountCiphertext,
        String maskedNumber,
        BankAccountVerificationStatus verificationStatus,
        Boolean active,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}
