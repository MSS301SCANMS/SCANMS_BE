package com.scanms.product.dto.response;

import java.util.List;

public record LiveCommerceResponse(
        List<LiveOfferResponse> offers, List<LiveVoucherDropResponse> voucherDrops, String pinnedProductId,
        long pinRevision
) {
    public LiveCommerceResponse(List<LiveOfferResponse> offers, List<LiveVoucherDropResponse> voucherDrops, String pinnedProductId) {
        this(offers, voucherDrops, pinnedProductId, 0L);
    }
}
