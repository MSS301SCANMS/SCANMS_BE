package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreateWalletTransactionRequest;
import com.scanms.payment.dto.response.WalletTransactionResponse;

import java.util.List;

public interface WalletTransactionService {
    WalletTransactionResponse create(CreateWalletTransactionRequest request);

    WalletTransactionResponse getById(String id);

    List<WalletTransactionResponse> findAll();
}

