package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreateWalletRequest;
import com.scanms.payment.dto.response.WalletResponse;

import java.util.List;

public interface WalletService {
    WalletResponse create(CreateWalletRequest request);

    WalletResponse getById(String id);

    List<WalletResponse> findAll();
}

