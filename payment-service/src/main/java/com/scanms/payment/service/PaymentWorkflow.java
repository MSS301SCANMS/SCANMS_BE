package com.scanms.payment.service;

import com.scanms.payment.dto.MoneyDtos.*;
import com.scanms.payment.entity.Payment;

import java.util.Map;

public interface PaymentWorkflow {
    PageView<PaymentView> history(int page, int size);

    PaymentView create(PaymentInput input);

    PaymentView topup(TopUpInput input);

    PaymentView payWallet(WalletPayInput input);

    PaymentView status(String id, boolean refresh);

    PaymentView cancel(String id);

    PaymentView resume(String id);

    void webhook(Map<String, Object> envelope);

    PaymentView refundUnapplied(String id, String reason);

    PaymentView refundSurplus(String id, String reason);

    PaymentView view(Payment p);
}
