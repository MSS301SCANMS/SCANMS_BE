package com.scanms.payment.service;

import com.scanms.payment.dto.request.CreatePaymentRequest;
import com.scanms.payment.dto.response.PaymentResponse;
import java.util.*;

public interface PaymentService {
    PaymentResponse create(CreatePaymentRequest request);
    PaymentResponse getById(String id);
    List<PaymentResponse> findAll();
}
