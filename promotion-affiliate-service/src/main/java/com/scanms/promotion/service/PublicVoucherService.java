package com.scanms.promotion.service;

import java.util.List;
import java.util.Map;

public interface PublicVoucherService {
    List<Map<String, Object>> platform(String storeId);

    List<Map<String, Object>> listByStore(String storeId);
}
