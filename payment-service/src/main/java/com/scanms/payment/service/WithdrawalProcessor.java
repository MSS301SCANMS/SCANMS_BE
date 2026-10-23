package com.scanms.payment.service;

import com.scanms.payment.dto.MoneyDtos.WithdrawalView;
import java.util.Map;

public interface WithdrawalProcessor {
    WithdrawalView execute(String id);

    WithdrawalView reconcile(String id);

    WithdrawalView notification(Map<String,Object> payload,String signature);
}
