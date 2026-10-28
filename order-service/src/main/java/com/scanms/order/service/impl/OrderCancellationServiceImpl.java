package com.scanms.order.service.impl;

import com.scanms.order.constant.OrderStatus;
import com.scanms.order.constant.SellerOrderStatus;
import com.scanms.order.dto.request.OrderCancellationDecision;
import com.scanms.order.dto.request.OrderCancellationRequest;
import com.scanms.order.entity.Order;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.repository.OrderRepository;
import com.scanms.order.repository.SellerOrderRepository;
import com.scanms.order.repository.ShipmentRepository;
import com.scanms.order.service.CommerceAccess;
import com.scanms.order.service.OrderCancellationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional
public class OrderCancellationServiceImpl implements OrderCancellationService {
    private final OrderRepository orders;
    private final SellerOrderRepository sellers;
    private final ShipmentRepository shipments;
    private final EntityManager em;
    private final CommerceAccess access;

    @Override
    public Map<String, Object> request(String orderId, OrderCancellationRequest input, Jwt jwt) {
        String customer = access.customer(jwt);
        Order order = orders.lockById(orderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (!customer.equals(order.getCustomerId())) throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        var cancellation = cancellation(order);
        if (order.getStatus() == OrderStatus.CANCELLED || "REQUESTED".equals(cancellation.get("state")))
            return cancellation;
        var rows = lockedSellers(order);
        requireCancellable(order, rows);
        if (input.reason() == null || input.reason().isBlank() || input.reason().length() > 500)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do hủy đơn (tối đa 500 ký tự).");
        var history = new ArrayList<Object>();
        if (cancellation.get("history") instanceof List<?> previous) history.addAll(previous);
        if (!cancellation.isEmpty()) {
            var old = new LinkedHashMap<>(cancellation);
            old.remove("history");
            history.add(old);
        }
        cancellation = new LinkedHashMap<>();
        cancellation.put("history", history);
        cancellation.put("reason", input.reason().trim());
        cancellation.put("requestedAt", Instant.now().toString());
        cancellation.put("requestedBy", customer);
        cancellation.put("refundDestination", "SCANMS_WALLET");
        cancellation.put("state", "REQUESTED");
        cancellation.put("decisions", Map.of());
        if (rows.stream().allMatch(row -> row.getStatus() == SellerOrderStatus.PENDING))
            cancel(order, rows, cancellation);
        else save(order, cancellation);
        return cancellation;
    }

    @Override
    public Map<String, Object> decide(String sellerOrderId, OrderCancellationDecision input, Jwt jwt) {
        var seller = sellers.findById(sellerOrderId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        access.shop(jwt, seller.getStoreId());
        var order = orders.lockById(seller.getOrderId()).orElseThrow();
        var rows = lockedSellers(order);
        var cancellation = cancellation(order);
        if (order.getStatus() == OrderStatus.CANCELLED && input.approved()) return cancellation;
        if (!"REQUESTED".equals(cancellation.get("state")))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn không có yêu cầu hủy đang chờ xử lý.");
        requireCancellable(order, rows);
        if (!input.approved() && (input.reason() == null || input.reason().isBlank()))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vui lòng nhập lý do từ chối hủy đơn.");
        var decisions = new LinkedHashMap<String, Object>();
        if (cancellation.get("decisions") instanceof Map<?, ?> old)
            old.forEach((key, value) -> decisions.put(key.toString(), value));
        decisions.put(seller.getStoreId(), Map.of("approved", input.approved(), "reason", Objects.toString(input.reason(), "").trim(), "decidedAt", Instant.now().toString()));
        cancellation.put("decisions", decisions);
        if (!input.approved()) {
            cancellation.put("state", "REJECTED");
            cancellation.put("decidedAt", Instant.now().toString());
            save(order, cancellation);
        } else if (rows.stream().allMatch(row -> decisions.get(row.getStoreId()) instanceof Map<?, ?> decision && Boolean.TRUE.equals(decision.get("approved")))) {
            cancel(order, rows, cancellation);
        } else save(order, cancellation);
        return cancellation;
    }

    private List<SellerOrder> lockedSellers(Order order) {
        var rows = sellers.findByOrderId(order.getOrderId()).stream().sorted(Comparator.comparing(SellerOrder::getSellerOrderId)).toList();
        rows.forEach(row -> em.refresh(row, LockModeType.PESSIMISTIC_WRITE));
        return rows;
    }

    private void requireCancellable(Order order, List<SellerOrder> rows) {
        if (!Set.of(OrderStatus.PENDING, OrderStatus.AWAITING_PAYMENT, OrderStatus.PAID, OrderStatus.PROCESSING).contains(order.getStatus())
                || rows.stream().anyMatch(row -> !Set.of(SellerOrderStatus.PENDING, SellerOrderStatus.CONFIRMED, SellerOrderStatus.PROCESSING).contains(row.getStatus())
                || row.getDeliveredAt() != null || shipments.findBySellerOrderId(row.getSellerOrderId()).isPresent()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Đơn đã bàn giao vận chuyển hoặc không còn đủ điều kiện hủy. Vui lòng dùng luồng trả hàng khi đủ điều kiện.");
    }

    private void cancel(Order order, List<SellerOrder> rows, Map<String, Object> cancellation) {
        order.setStatus(OrderStatus.CANCELLED);
        rows.forEach(row -> row.setStatus(SellerOrderStatus.CANCELLED));
        cancellation.put("state", "CANCELLED");
        cancellation.put("cancelledAt", Instant.now().toString());
        cancellation.put("refundStatus", "PENDING");
        save(order, cancellation);
    }

    private Map<String, Object> cancellation(Order order) {
        var result = new LinkedHashMap<String, Object>();
        if (order.getSagaState() != null && order.getSagaState().get("cancellation") instanceof Map<?, ?> value)
            value.forEach((key, item) -> result.put(key.toString(), item));
        return result;
    }

    private void save(Order order, Map<String, Object> cancellation) {
        var saga = new LinkedHashMap<>(order.getSagaState() == null ? Map.of() : order.getSagaState());
        saga.put("cancellation", new LinkedHashMap<>(cancellation));
        order.setSagaState(saga);
        orders.save(order);
    }
}
