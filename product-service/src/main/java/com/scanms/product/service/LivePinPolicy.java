package com.scanms.product.service;

import com.scanms.product.constant.*;
import com.scanms.product.entity.Livestream;
import com.scanms.product.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
@RequiredArgsConstructor
public class LivePinPolicy {
    private final ProductRepository products;
    private final ProductVariantRepository variants;
    private final StoreRepository stores;

    public boolean available(Livestream live, String id) {
        if (id == null || live.getStatus() != LivestreamStatus.LIVE || live.getProductRefs() == null
                || !(live.getProductRefs().get("productIds") instanceof Collection<?> ids) || !ids.contains(id))
            return false;
        return products.findById(id).filter(p -> p.getStatus() == ProductStatus.ACTIVE && live.getStoreId().equals(p.getStoreId())).isPresent()
                && stores.findById(live.getStoreId()).filter(s -> s.getApprovalStatus() == StoreStatus.APPROVED).isPresent()
                && variants.findByProductIdIn(List.of(id)).stream().anyMatch(v -> v.getStatus() == ProductVariantStatus.ACTIVE
                && v.getStockQuantity() != null && v.getStockQuantity() > 0 && v.getPriceVnd() != null && v.getPriceVnd() > 0);
    }

    public long revision(Livestream live) {
        return live.getProductRefs() != null && live.getProductRefs().get("pinRevision") instanceof Number n ? n.longValue() : 0L;
    }

    public void set(Livestream live, String id) {
        var refs = new LinkedHashMap<String, Object>(live.getProductRefs() == null ? Map.of() : live.getProductRefs());
        if (refs.containsKey("pinnedProductId") && Objects.equals(id, refs.get("pinnedProductId"))) return;
        refs.put("pinRevision", Math.addExact(revision(live), 1L));
        refs.put("pinnedProductId", id);
        live.setProductRefs(refs);
    }

    public void clearUnavailable(Livestream live) {
        if (live.getProductRefs() != null && live.getProductRefs().get("pinnedProductId") instanceof String id && !available(live, id))
            set(live, null);
    }
}
