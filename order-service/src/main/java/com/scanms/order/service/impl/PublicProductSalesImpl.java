package com.scanms.order.service.impl;

import com.scanms.order.repository.OrderItemRepository;
import com.scanms.order.repository.ReturnRequestRepository;
import com.scanms.order.service.PublicProductSales;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PublicProductSalesImpl implements PublicProductSales {
    private final OrderItemRepository items;
    private final ReturnRequestRepository returns;

    @Override
    public Map<String, Long> soldQuantities(List<String> ids) {
        if (ids == null || ids.size() > 100 || ids.stream().anyMatch(id -> id == null || !id.matches("[A-Za-z0-9_-]{1,100}")))
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid product sales batch");
        var counts = new LinkedHashMap<String, Long>();
        ids.forEach(id -> counts.put(id, 0L));
        if (ids.isEmpty()) return counts;
        items.deliveredQuantities(ids).forEach(row -> counts.put(row[0].toString(), ((Number) row[1]).longValue()));
        returns.refundedQuantities(ids).forEach(row -> counts.computeIfPresent(row[0].toString(), (id, value) -> Math.max(0, value - ((Number) row[1]).longValue())));
        return counts;
    }
}
