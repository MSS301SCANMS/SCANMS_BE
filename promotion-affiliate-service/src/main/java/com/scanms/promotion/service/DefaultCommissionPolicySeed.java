package com.scanms.promotion.service;

import com.scanms.promotion.constant.*;
import com.scanms.promotion.entity.CommissionPolicy;
import com.scanms.promotion.repository.CommissionPolicyRepository;
import java.math.BigDecimal;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;

@Service
@RequiredArgsConstructor
public class DefaultCommissionPolicySeed {
    private final CommissionPolicyRepository policies;
    private final JdbcTemplate jdbc;
    @Transactional
    public void seed() {
        jdbc.execute("SELECT pg_advisory_xact_lock(624303)");
        // An existing platform policy (even disabled/expired) is an operator decision.
        // Do not silently reactivate or replace it on application restart.
        if (policies.findAll().stream().anyMatch(p -> p.getScopeType() == CommissionPolicyScope.PLATFORM)) return;
        policies.saveAndFlush(CommissionPolicy.builder().policyName("Hoa hồng mặc định 10%")
                .scopeType(CommissionPolicyScope.PLATFORM).commissionRate(BigDecimal.TEN)
                .returnWindowDays(14).validFrom(Instant.now()).status(CommissionPolicyStatus.ACTIVE).priority(-100).build());
    }
}
