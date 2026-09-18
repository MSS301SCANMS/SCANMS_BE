package com.scanms.payment.service;

import com.scanms.payment.dto.request.RegisterBankAccountRequest;
import com.scanms.payment.dto.response.BankAccountResponse;
import java.util.*;

public interface BankAccountService {
    BankAccountResponse create(RegisterBankAccountRequest request);
    BankAccountResponse getById(UUID id);
    List<BankAccountResponse> findAll();
}
