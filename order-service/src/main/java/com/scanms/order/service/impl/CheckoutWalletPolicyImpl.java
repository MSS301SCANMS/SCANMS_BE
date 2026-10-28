package com.scanms.order.service.impl;

import com.scanms.order.client.CheckoutWalletClient;
import com.scanms.order.service.CheckoutWalletPolicy;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.text.NumberFormat;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CheckoutWalletPolicyImpl implements CheckoutWalletPolicy {
    private final CheckoutWalletClient payments;

    @Override
    public void requireFunds(Jwt jwt, long amount) {
        try {
            var response = payments.wallet("Bearer " + jwt.getTokenValue(), "CUSTOMER");
            if (response == null || !(response.get("result") instanceof java.util.Map<?, ?> wallet)
                    || !(wallet.get("availableBalanceVnd") instanceof Number balance) || !"ACTIVE".equals(wallet.get("status")))
                throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa xác minh được số dư ví. Vui lòng thử lại.");
            if (balance.longValue() < amount) {
                var money = NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN"));
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ví có " + money.format(balance.longValue()) + "đ, đơn cần " + money.format(amount)
                        + "đ. Bạn còn thiếu " + money.format(amount - balance.longValue()) + "đ. Vui lòng nạp thêm tiền hoặc đổi phương thức thanh toán.");
            }
        } catch (feign.FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Không kiểm tra được ví. Vui lòng thử lại hoặc chọn phương thức khác.");
        }
    }
}
