package com.scanms.order.service;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;

@Component @RequiredArgsConstructor
public class CheckoutShippingPolicy {
    private final Environment environment;
    @Value("${scanms.checkout.shipping-fee-per-store-vnd:0}")
    private long fee;
    @Value("${scanms.checkout.shipping-simulation-enabled:false}")
    private boolean simulation;

    public long perStore() {
        if(fee<0 || fee>10_000_000) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Cấu hình phí vận chuyển không hợp lệ.");
        if(simulation && (!environment.acceptsProfiles(Profiles.of("local","test")) || environment.acceptsProfiles(Profiles.of("prod","production"))))
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,"Phí vận chuyển mô phỏng chỉ dùng trong môi trường local/test.");
        return fee;
    }
    public long total(long stores) {
        return Math.multiplyExact(perStore(),stores);
    }

    public Map<String,Object> snapshot() {
        return Map.of("shippingMode",simulation?"SIMULATED":"FIXED","shippingFeePerStoreVnd",perStore(),
                "shippingPayer","CUSTOMER","shippingSettlementResponsible","PLATFORM",
                "shippingSettlementStatus",simulation?"SIMULATED":"UNSETTLED");
    }
}
