package com.scanms.promotion.config;

import com.scanms.promotion.service.DefaultCommissionPolicySeed;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "scanms.commission.default-policy-enabled", havingValue = "true")
public class DefaultCommissionPolicyInitializer implements ApplicationRunner {
    private final DefaultCommissionPolicySeed seed;
    @Override public void run(ApplicationArguments args) { seed.seed(); }
}
