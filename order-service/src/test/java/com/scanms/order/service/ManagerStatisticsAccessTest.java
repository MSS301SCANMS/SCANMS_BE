package com.scanms.order.service;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class ManagerStatisticsAccessTest {
    Jwt jwt(String role) { return Jwt.withTokenValue("fixture").header("alg","none").subject("subject").claim("realm_access",Map.of("roles",List.of(role))).build(); }
    @Test void deniesShopAndCustomerBeforeAnyUpstreamCall() {
        var access=new ManagerStatisticsAccess();
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.requireManager(jwt("SHOP_OWNER"))).getStatusCode().value());
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.requireManager(jwt("CUSTOMER"))).getStatusCode().value());
        assertEquals(401,assertThrows(ResponseStatusException.class,()->access.requireManager(null)).getStatusCode().value());
    }
    @Test void requiresMatchingActiveUserAndPropagatesDependencyOutage() {
        var access=new ManagerStatisticsAccess(); var builder=RestClient.builder(); var server=MockRestServiceServer.bindTo(builder).build();
        ReflectionTestUtils.setField(access,"http",builder.build()); ReflectionTestUtils.setField(access,"userUrl","http://user.test");
        server.expect(requestTo("http://user.test/api/v1/users/me")).andExpect(header("Authorization","Bearer fixture")).andRespond(withSuccess("{\"result\":{\"status\":\"ACTIVE\",\"identitySubject\":\"subject\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user.test/api/v1/users/me")).andRespond(withSuccess("{\"result\":{\"status\":\"ACTIVE\",\"identitySubject\":\"someone-else\"}}",MediaType.APPLICATION_JSON));
        server.expect(requestTo("http://user.test/api/v1/users/me")).andRespond(withServerError());
        access.requireManager(jwt("MANAGER"));
        assertEquals(403,assertThrows(ResponseStatusException.class,()->access.requireManager(jwt("ADMIN"))).getStatusCode().value());
        assertEquals(503,assertThrows(ResponseStatusException.class,()->access.requireManager(jwt("MANAGER"))).getStatusCode().value()); server.verify();
    }
}
