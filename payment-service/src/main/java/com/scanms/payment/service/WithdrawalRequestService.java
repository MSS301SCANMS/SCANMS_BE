package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreateWithdrawalRequest;
import com.scanms.payment.dto.response.WithdrawalRequestResponse;
import java.util.*;

public interface WithdrawalRequestService {
    WithdrawalRequestResponse create(CreateWithdrawalRequest request);
    WithdrawalRequestResponse getById(String id);
    List<WithdrawalRequestResponse> findAll();
}
