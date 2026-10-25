package com.scanms.order.service.impl;

import com.scanms.order.constant.DiscountFundingType;
import com.scanms.order.constant.DiscountSourceType;
import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.entity.DiscountAllocation;
import com.scanms.order.entity.OrderItem;
import com.scanms.order.exception.AppException;
import com.scanms.order.exception.ErrorCode;
import com.scanms.order.mapper.DiscountAllocationMapper;
import com.scanms.order.repository.DiscountAllocationRepository;
import com.scanms.order.service.DiscountAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional
public class DiscountAllocationServiceImpl implements DiscountAllocationService {
    private final DiscountAllocationRepository repository;
    private final DiscountAllocationMapper mapper;

    public DiscountAllocationResponse create(CreateDiscountAllocationRequest request) {
        return mapper.toResponse(repository.save(mapper.toEntity(request)));
    }

    @Transactional(readOnly = true)
    public DiscountAllocationResponse getById(String id) {
        return repository.findById(id).map(mapper::toResponse)
                .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND, "DiscountAllocation not found: " + id));
    }

    @Transactional(readOnly = true)
    public List<DiscountAllocationResponse> findAll() {
        return repository.findAll().stream().map(mapper::toResponse).toList();
    }

    @Override
    public Map<String, Long> allocateOrderDiscount(List<OrderItem> items, Long totalDiscountVnd) {
        Map<String, Long> allocationMap = new LinkedHashMap<>();
        if (items == null || items.isEmpty() || totalDiscountVnd == null || totalDiscountVnd <= 0) {
            if (items != null) {
                for (OrderItem item : items) {
                    allocationMap.put(item.getOrderItemId(), 0L);
                }
            }
            return allocationMap;
        }

        long totalGross = items.stream().mapToLong(OrderItem::getGrossAmountVnd).sum();
        if (totalGross <= 0) {
            for (OrderItem item : items) {
                allocationMap.put(item.getOrderItemId(), 0L);
            }
            return allocationMap;
        }

        long effectiveDiscount = Math.min(totalDiscountVnd, totalGross);
        long currentAllocatedSum = 0L;
        String maxGrossItemId = null;
        long maxGross = -1;

        for (OrderItem item : items) {
            long itemGross = item.getGrossAmountVnd();
            if (itemGross > maxGross) {
                maxGross = itemGross;
                maxGrossItemId = item.getOrderItemId();
            }

            long itemAlloc = Math.round((double) itemGross * effectiveDiscount / totalGross);
            itemAlloc = Math.min(itemAlloc, itemGross);
            allocationMap.put(item.getOrderItemId(), itemAlloc);
            currentAllocatedSum += itemAlloc;
        }

        long drift = effectiveDiscount - currentAllocatedSum;
        if (drift != 0 && maxGrossItemId != null) {
            final String targetId = maxGrossItemId;
            long currentAlloc = allocationMap.get(targetId);
            long itemGross = items.stream()
                    .filter(i -> i.getOrderItemId().equals(targetId))
                    .mapToLong(OrderItem::getGrossAmountVnd)
                    .findFirst()
                    .orElse(currentAlloc);

            long adjusted = Math.min(itemGross, Math.max(0, currentAlloc + drift));
            allocationMap.put(targetId, adjusted);
        }

        return allocationMap;
    }

    @Override
    public List<DiscountAllocation> createAllocations(
            List<OrderItem> items,
            String voucherId,
            DiscountFundingType fundingType,
            Map<String, Long> itemAllocations) {
        List<DiscountAllocation> list = new ArrayList<>();
        if (items == null || itemAllocations == null) return list;

        for (OrderItem item : items) {
            Long alloc = itemAllocations.getOrDefault(item.getOrderItemId(), 0L);
            if (alloc > 0) {
                DiscountAllocation da = DiscountAllocation.builder()
                        .orderItemId(item.getOrderItemId())
                        .voucherId(voucherId != null && !voucherId.isBlank() ? voucherId : "VOUCHER_ORDER")
                        .sourceType(DiscountSourceType.VOUCHER)
                        .fundedBy(fundingType != null ? fundingType : DiscountFundingType.PLATFORM)
                        .allocatedAmountVnd(alloc)
                        .ruleSnapshotJson(String.format("{\"allocated\":%d,\"gross\":%d,\"unitPrice\":%d,\"quantity\":%d}",
                                alloc, item.getGrossAmountVnd(), item.getUnitPrice(), item.getQuantity()))
                        .build();
                list.add(repository.save(da));
            }
        }
        return list;
    }
}

