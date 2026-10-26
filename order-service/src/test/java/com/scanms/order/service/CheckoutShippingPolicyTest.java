package com.scanms.order.service;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
class CheckoutShippingPolicyTest {
    CheckoutShippingPolicy policy(String... profiles) {
        var env=new MockEnvironment();env.setActiveProfiles(profiles);var p=new CheckoutShippingPolicy(env);
        ReflectionTestUtils.setField(p,"fee",30000L);ReflectionTestUtils.setField(p,"simulation",true);return p;
    }
    @Test void simulationChargesOncePerStoreAndDescribesWhoPays() {
        var p=policy("local");assertEquals(60000,p.total(2));
        assertEquals("SIMULATED",p.snapshot().get("shippingMode"));assertEquals("CUSTOMER",p.snapshot().get("shippingPayer"));
        assertEquals("PLATFORM",p.snapshot().get("shippingSettlementResponsible"));assertEquals("SIMULATED",p.snapshot().get("shippingSettlementStatus"));
    }
    @Test void simulationCannotBeEnabledOnProductionEvenWithLocalProfile() {
        assertThrows(ResponseStatusException.class,()->policy("prod").perStore());
        assertThrows(ResponseStatusException.class,()->policy("local","production").perStore());
        assertThrows(ResponseStatusException.class,()->policy().perStore());
    }
    @Test void invalidConfigurationCannotBecomeFreeShipping() {
        var p=policy("test");ReflectionTestUtils.setField(p,"fee",-1L);assertThrows(ResponseStatusException.class,p::perStore);
        ReflectionTestUtils.setField(p,"fee",10000001L);assertThrows(ResponseStatusException.class,p::perStore);
    }
}
