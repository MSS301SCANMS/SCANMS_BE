package com.scanms.product.service;

import java.util.List;
import java.util.Map;

public interface PublicProductSalesService {
    Map<String, Long> counts(List<String> productIds);
}
