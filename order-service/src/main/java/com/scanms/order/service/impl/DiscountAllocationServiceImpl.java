package com.scanms.order.service.impl;

import com.scanms.order.dto.request.CreateDiscountAllocationRequest;
import com.scanms.order.dto.response.DiscountAllocationResponse;
import com.scanms.order.exception.*;
import com.scanms.order.mapper.DiscountAllocationMapper;
import com.scanms.order.repository.DiscountAllocationRepository;
import com.scanms.order.service.DiscountAllocationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

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
    public Map<String, Long> allocateOrderDiscount(List<com.scanms.order.entity.OrderItem> items, Long totalDiscountVnd) {
        Map<String, Long> allocationMap = new LinkedHashMap<>();
        if (items == null || items.isEmpty() || totalDiscountVnd == null || totalDiscountVnd <= 0) {
            if (items != null) {
                for (com.scanms.order.entity.OrderItem item : items) {
                    allocationMap.put(item.getOrderItemId(), 0L);
                }
            }
            return allocationMap;
        }

        long totalGross = items.stream().mapToLong(com.scanms.order.entity.OrderItem::getGrossAmountVnd).sum();
        if (totalGross <= 0) {
            for (com.scanms.order.entity.OrderItem item : items) {
                allocationMap.put(item.getOrderItemId(), 0L);
            }
            return allocationMap;
        }

        long effectiveDiscount = Math.min(totalDiscountVnd, totalGross);
        long currentAllocatedSum = 0L;
        String maxGrossItemId = null;
        long maxGross = -1;

        for (com.scanms.order.entity.OrderItem item : items) {
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
                    .mapToLong(com.scanms.order.entity.OrderItem::getGrossAmountVnd)
                    .findFirst()
                    .orElse(currentAlloc);

            long adjusted = Math.min(itemGross, Math.max(0, currentAlloc + drift));
            allocationMap.put(targetId, adjusted);
        }

        return allocationMap;
    }

    @Override
    public List<com.scanms.order.entity.DiscountAllocation> createAllocations(
            List<com.scanms.order.entity.OrderItem> items,
            String voucherId,
            com.scanms.order.constant.DiscountFundingType fundingType,
            Map<String, Long> itemAllocations) {
        List<com.scanms.order.entity.DiscountAllocation> list = new ArrayList<>();
        if (items == null || itemAllocations == null) return list;

        for (com.scanms.order.entity.OrderItem item : items) {
            Long alloc = itemAllocations.getOrDefault(item.getOrderItemId(), 0L);
            if (alloc > 0) {
                com.scanms.order.entity.DiscountAllocation da = com.scanms.order.entity.DiscountAllocation.builder()
                        .orderItemId(item.getOrderItemId())
                        .voucherId(voucherId != null && !voucherId.isBlank() ? voucherId : "VOUCHER_ORDER")
                        .sourceType(com.scanms.order.constant.DiscountSourceType.VOUCHER)
                        .fundedBy(fundingType != null ? fundingType : com.scanms.order.constant.DiscountFundingType.PLATFORM)
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

