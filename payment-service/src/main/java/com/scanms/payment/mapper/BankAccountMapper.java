package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.RegisterBankAccountRequest;
import com.scanms.payment.dto.response.BankAccountResponse;
import com.scanms.payment.entity.BankAccount;
import org.springframework.stereotype.Component;

@Component
public class BankAccountMapper {
    public BankAccount toEntity(RegisterBankAccountRequest request) {
        return BankAccount.builder()
                .storeId(request.storeId())
                .collaboratorId(request.collaboratorId())
                .bankCode(request.bankCode())
                .holderName(request.holderName())
                .accountCiphertext(request.accountCiphertext())
                .maskedNumber(request.maskedNumber())
                .verificationStatus(request.verificationStatus())
                .active(request.active())
                .build();
    }

    public BankAccountResponse toResponse(BankAccount entity) {
        return new BankAccountResponse(
                entity.getBankAccountId(),
                entity.getStoreId(),
                entity.getCollaboratorId(),
                entity.getBankCode(),
                entity.getHolderName(),
                entity.getAccountCiphertext(),
                entity.getMaskedNumber(),
                entity.getVerificationStatus(),
                entity.getActive(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }
}
