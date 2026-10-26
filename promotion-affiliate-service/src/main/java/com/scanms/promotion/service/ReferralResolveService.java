package com.scanms.promotion.service;

import java.util.Map;

public interface ReferralResolveService {
    Map<String, String> resolve(String token);
}
