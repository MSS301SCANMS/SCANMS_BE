package com.scanms.payment.mapper;

import com.scanms.payment.dto.request.RegisterBankAccountRequest;
import com.scanms.payment.dto.response.BankAccountResponse;
import com.scanms.payment.entity.BankAccount;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface BankAccountMapper {
    BankAccount toEntity(RegisterBankAccountRequest request);

    // Không bao giờ trả accountCiphertext ra response — giữ đúng hành vi cũ (luôn null).
    @Mapping(target = "accountCiphertext", ignore = true)
    BankAccountResponse toResponse(BankAccount entity);
}
