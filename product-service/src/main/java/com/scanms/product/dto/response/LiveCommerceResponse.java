package com.scanms.product.dto.response;

import java.util.List;

public record LiveCommerceResponse(
        List<LiveOfferResponse> offers, List<LiveVoucherDropResponse> voucherDrops, String pinnedProductId
) {}
