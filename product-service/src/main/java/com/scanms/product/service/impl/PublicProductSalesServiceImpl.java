package com.scanms.product.service.impl;

import com.scanms.product.client.PublicProductSalesClient;
import com.scanms.product.service.PublicProductSalesService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
@RequiredArgsConstructor
public class PublicProductSalesServiceImpl implements PublicProductSalesService {
    private final PublicProductSalesClient orders;

    @Override
    public Map<String, Long> counts(List<String> ids) {
        if (ids.isEmpty()) return Map.of();
        try {
            var response = orders.sold(ids);
            if (response == null || !(response.get("result") instanceof Map<?, ?> values)) return Map.of();
            var result = new HashMap<String, Long>();
            ids.forEach(id -> {
                if (values.get(id) instanceof Number count && count.longValue() >= 0) result.put(id, count.longValue());
            });
            return result;
        } catch (FeignException ex) {
            return Map.of(); // Catalog remains available; the UI labels the sales count as unavailable.
        }
    }
}
