package com.scanms.order.service.impl;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.dto.request.OrderCancellationRefund;
import com.scanms.order.entity.Order;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.service.CancellationRefundSource;
import com.scanms.order.service.CommerceAccess;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.time.Instant;

@Service
@RequiredArgsConstructor
@Transactional
public class CancellationRefundSourceImpl implements CancellationRefundSource {
    private final OrderRepository orders;
    private final CommerceAccess access;

    @Override
    public Map<String, Object> basis(String id, Jwt jwt) {
        trusted(jwt, false);
        var order = cancelled(id);
        var value = new LinkedHashMap<String, Object>();
        value.put("orderId", id);
        value.put("customerId", order.getCustomerId());
        value.put("amountVnd", order.getPayableVnd());
        value.put("currency", order.getCurrency());
        if (order.getSagaState() != null && order.getSagaState().get("paymentId") != null)
            value.put("paymentId", order.getSagaState().get("paymentId"));
        return value;
    }

    @Override
    public Map<String, Object> acknowledge(String id, OrderCancellationRefund input, Jwt jwt) {
        trusted(jwt, true);
        var order = cancelled(id);
        if (!Objects.equals(order.getPayableVnd(), input.amountVnd()) || input.amountVnd() <= 0)
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation refund total mismatch");
        var saga = new LinkedHashMap<>(order.getSagaState() == null ? Map.of() : order.getSagaState());
        if (saga.get("paymentId") != null && !saga.get("paymentId").equals(input.paymentId()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation payment reference mismatch");
        var cancellation = new LinkedHashMap<String, Object>();
        if (saga.get("cancellation") instanceof Map<?, ?> previous)
            previous.forEach((key, value) -> cancellation.put(key.toString(), value));
        if (cancellation.get("refundReference") != null && !input.transactionId().equals(cancellation.get("refundReference")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Cancellation was already refunded with another reference");
        cancellation.put("refundStatus", "REFUNDED");
        cancellation.put("refundReference", input.transactionId());
        cancellation.put("refundAmountVnd", input.amountVnd());
        cancellation.putIfAbsent("refundedAt", Instant.now().toString());
        cancellation.put("refundDestination", "SCANMS_WALLET");
        saga.put("cancellation", cancellation);
        order.setSagaState(saga);
        orders.save(order);
        return cancellation;
    }

    private Order cancelled(String id) {
        var order = orders.lockById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (order.getStatus() != OrderStatus.CANCELLED || !"VND".equals(order.getCurrency()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only cancelled VND orders can be refunded");
        return order;
    }

    private void trusted(Jwt jwt, boolean paymentOnly) {
        if (!access.role(jwt, "PAYMENT_INTERNAL") && (paymentOnly || !access.role(jwt, "ORDER_INTERNAL")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
}
