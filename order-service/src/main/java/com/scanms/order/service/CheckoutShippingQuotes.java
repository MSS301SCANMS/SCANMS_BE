package com.scanms.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tổng hợp và tính toán cước phí vận chuyển đa gian hàng (Multi-Store Checkout).
 * Hỗ trợ chuyển đổi linh hoạt giữa chính sách cước cố định (Fixed) và Sandbox GHN.
 */
@Component
@RequiredArgsConstructor
public class CheckoutShippingQuotes {

    private final CheckoutShippingPolicy fixed;
    private final GhnSandboxShipping ghn;

    public record Quote(
            Map<String, Long> fees,
            Map<String, Map<String, Object>> snapshots,
            Map<String, Object> mode
    ) {
        public long total() {
            return fees.values().stream().reduce(0L, Math::addExact);
        }
    }

    public Quote quote(List<Map<String, Object>> lines, CheckoutService.ShippingDestination destination) {
        var groups = new LinkedHashMap<String, List<Map<String, Object>>>();
        for (var line : lines) {
            groups.computeIfAbsent(line.get("storeId").toString(), id -> new ArrayList<>()).add(line);
        }

        var fees = new LinkedHashMap<String, Long>();
        var snapshots = new LinkedHashMap<String, Map<String, Object>>();
        boolean sandbox = ghn.enabled();

        for (var group : groups.entrySet()) {
            if (sandbox) {
                var quote = ghn.quote(group.getKey(), destination, group.getValue());
                fees.put(group.getKey(), quote.fee());
                snapshots.put(group.getKey(), quote.snapshot());
            } else {
                fees.put(group.getKey(), fixed.perStore());
                snapshots.put(group.getKey(), fixed.snapshot());
            }
        }

        return new Quote(fees, snapshots, sandbox ? ghn.mode() : fixed.snapshot());
    }
}
