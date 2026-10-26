package com.scanms.product.service;

import java.util.Map;
import java.util.Collection;
import java.math.BigDecimal;

public interface PublicReviewService {
    record Rating(BigDecimal averageRating, long reviewCount) {}
    Map<String, Rating> ratings(Collection<String> productIds);
    Map<String, Object> summary(String productId);
}
