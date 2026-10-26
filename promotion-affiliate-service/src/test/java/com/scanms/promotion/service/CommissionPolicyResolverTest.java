package com.scanms.promotion.service;

import com.scanms.promotion.constant.CommissionPolicyScope;
import com.scanms.promotion.constant.CommissionPolicyStatus;
import com.scanms.promotion.entity.CommissionPolicy;
import com.scanms.promotion.mapper.CommissionPolicyMapper;
import com.scanms.promotion.repository.CommissionPolicyRepository;
import com.scanms.promotion.service.impl.CommissionPolicyServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import org.mapstruct.factory.Mappers;

/**
 * DoD: priority COLLABORATOR → LIVESTREAM → PRODUCT → STORE → PLATFORM;
 * chỉ ACTIVE + trong validFrom/validTo.
 */
class CommissionPolicyResolverTest {

    private CommissionPolicyRepository repository;
    private CommissionPolicyServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(CommissionPolicyRepository.class);
        service = new CommissionPolicyServiceImpl(repository, Mappers.getMapper(CommissionPolicyMapper.class));
        when(repository.findByCollaboratorIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findByLivestreamIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findByProductIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findByStoreIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        when(repository.findByScopeTypeAndStatusOrderByPriorityDesc(any(), any()))
                .thenReturn(List.of());
    }

    private CommissionPolicy policy(String id, CommissionPolicyScope scope, int priority, Instant from, Instant to) {
        return CommissionPolicy.builder()
                .policyId(id)
                .policyName(id)
                .scopeType(scope)
                .commissionRate(BigDecimal.TEN)
                .returnWindowDays(14)
                .validFrom(from)
                .validTo(to)
                .status(CommissionPolicyStatus.ACTIVE)
                .priority(priority)
                .build();
    }

    @Test
    void prefersCollaboratorOverPlatform() {
        var collab = policy("c1", CommissionPolicyScope.COLLABORATOR, 1, Instant.now().minusSeconds(60), null);
        var platform = policy("p1", CommissionPolicyScope.PLATFORM, 100, Instant.now().minusSeconds(60), null);
        when(repository.findByCollaboratorIdAndScopeTypeAndStatusOrderByPriorityDesc(
                eq("kol-1"), eq(CommissionPolicyScope.COLLABORATOR), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(collab));
        when(repository.findByScopeTypeAndStatusOrderByPriorityDesc(
                eq(CommissionPolicyScope.PLATFORM), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(platform));

        assertEquals("c1", service.resolvePolicy("kol-1", null, "prod", "store").getPolicyId());
    }

    @Test
    void fallsBackThroughLivestreamProductStoreThenPlatform() {
        var livestream = policy("l1", CommissionPolicyScope.LIVESTREAM, 1, Instant.now().minusSeconds(10), null);
        when(repository.findByLivestreamIdAndScopeTypeAndStatusOrderByPriorityDesc(
                eq("live-1"), eq(CommissionPolicyScope.LIVESTREAM), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(livestream));

        assertEquals("l1", service.resolvePolicy("kol-1", "live-1", "prod", "store").getPolicyId());

        when(repository.findByLivestreamIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        var product = policy("pr1", CommissionPolicyScope.PRODUCT, 1, Instant.now().minusSeconds(10), null);
        when(repository.findByProductIdAndScopeTypeAndStatusOrderByPriorityDesc(
                eq("prod"), eq(CommissionPolicyScope.PRODUCT), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(product));
        assertEquals("pr1", service.resolvePolicy("kol-1", "live-1", "prod", "store").getPolicyId());

        when(repository.findByProductIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        var store = policy("s1", CommissionPolicyScope.STORE, 1, Instant.now().minusSeconds(10), null);
        when(repository.findByStoreIdAndScopeTypeAndStatusOrderByPriorityDesc(
                eq("store"), eq(CommissionPolicyScope.STORE), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(store));
        assertEquals("s1", service.resolvePolicy("kol-1", "live-1", "prod", "store").getPolicyId());

        when(repository.findByStoreIdAndScopeTypeAndStatusOrderByPriorityDesc(any(), any(), any()))
                .thenReturn(List.of());
        var platform = policy("plat", CommissionPolicyScope.PLATFORM, 1, Instant.now().minusSeconds(10), null);
        when(repository.findByScopeTypeAndStatusOrderByPriorityDesc(
                eq(CommissionPolicyScope.PLATFORM), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(platform));
        assertEquals("plat", service.resolvePolicy("kol-1", "live-1", "prod", "store").getPolicyId());
    }

    @Test
    void ignoresExpiredAndFuturePolicies() {
        var expired = policy("old", CommissionPolicyScope.PLATFORM, 10,
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(10));
        var future = policy("future", CommissionPolicyScope.PLATFORM, 20,
                Instant.now().plusSeconds(3600), null);
        var active = policy("now", CommissionPolicyScope.PLATFORM, 1,
                Instant.now().minusSeconds(5), Instant.now().plusSeconds(3600));
        when(repository.findByScopeTypeAndStatusOrderByPriorityDesc(
                eq(CommissionPolicyScope.PLATFORM), eq(CommissionPolicyStatus.ACTIVE)))
                .thenReturn(List.of(expired, future, active));

        assertEquals("now", service.resolvePolicy(null, null, null, null).getPolicyId());
    }

    @Test
    void returnsNullWhenNoActivePolicy() {
        assertNull(service.resolvePolicy("kol", "live", "prod", "store"));
    }
}
