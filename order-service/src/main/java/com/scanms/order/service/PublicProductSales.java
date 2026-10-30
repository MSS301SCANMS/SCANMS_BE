package com.scanms.order.service;

import java.util.List;
import java.util.Map;

public interface PublicProductSales {
    Map<String, Long> soldQuantities(List<String> productIds);
}
