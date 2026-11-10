package com.scanms.order.service.impl;

import com.scanms.order.dto.response.OrderNotificationPage;
import com.scanms.order.entity.OrderNotificationRead;
import com.scanms.order.entity.SellerOrder;
import com.scanms.order.mapper.OrderNotificationMapper;
import com.scanms.order.repository.OrderNotificationReadRepository;
import com.scanms.order.service.CommerceAccess;
import com.scanms.order.service.OrderNotificationService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderNotificationServiceImpl implements OrderNotificationService {
    private final OrderNotificationReadRepository reads;
    private final OrderNotificationMapper mapper;
    private final CommerceAccess access;
    private final EntityManager em;

    @Override
    public OrderNotificationPage list(String category, int page, int limit, Jwt jwt) {
        String userId = access.customer(jwt);
        if (!Set.of("ALL", "ORDER", "PROMOTION", "SYSTEM").contains(category)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Loại thông báo không hợp lệ.");
        }
        int currentPage = Math.max(1, page);
        int size = Math.max(1, Math.min(100, limit));
        long unread = reads.unreadCount(userId);
        if (!Set.of("ALL", "ORDER").contains(category)) {
            return new OrderNotificationPage(List.of(), 0, currentPage, size, 0, unread, counts(unread));
        }
        // Delivery timestamps are the durable source. This also covers orders delivered before this API existed.
        var deliveries = reads.deliveries(userId, null, PageRequest.of(currentPage - 1, size));
        return new OrderNotificationPage(deliveries.getContent().stream().map(mapper::toResponse).toList(),
                deliveries.getTotalElements(), currentPage, size, deliveries.getTotalPages(), unread, counts(unread));
    }

    @Override
    public Map<String, Object> unread(Jwt jwt) {
        long count = reads.unreadCount(access.customer(jwt));
        return Map.of("unreadCount", count, "categoryUnreadCounts", counts(count));
    }

    @Override
    @Transactional
    public void markRead(String notificationId, Jwt jwt) {
        String userId = access.customer(jwt);
        if (notificationId == null || !notificationId.startsWith("delivery-") || notificationId.length() > 100) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo.");
        }
        String sellerId = notificationId.substring("delivery-".length());
        if (reads.deliveries(userId, sellerId, PageRequest.of(0, 1)).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Không tìm thấy thông báo.");
        }
        recordRead(userId, sellerId);
    }

    @Override
    @Transactional
    public void markAllRead(Jwt jwt) {
        String userId = access.customer(jwt);
        // Deterministic lock order keeps bulk/single reads idempotent under concurrent requests.
        for (String sellerId : reads.unreadSellerIds(userId)) {
            recordRead(userId, sellerId);
        }
    }

    private void recordRead(String userId, String sellerId) {
        if (em.find(SellerOrder.class, sellerId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        if (reads.existsByUserIdAndSellerOrderId(userId, sellerId)) {
            return;
        }
        var receipt = new OrderNotificationRead();
        receipt.setId(UUID.nameUUIDFromBytes((userId + ":ORDER_DELIVERED:" + sellerId).getBytes(StandardCharsets.UTF_8)).toString());
        receipt.setUserId(userId);
        receipt.setSellerOrderId(sellerId);
        receipt.setReadAt(Instant.now());
        reads.save(receipt);
    }

    private Map<String, Long> counts(long orderCount) {
        return Map.of("ORDER", orderCount, "PROMOTION", 0L, "SYSTEM", 0L);
    }
}
