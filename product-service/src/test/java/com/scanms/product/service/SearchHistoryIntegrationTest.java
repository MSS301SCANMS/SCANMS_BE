package com.scanms.product.service;

import com.scanms.product.dto.request.SearchEventRequest;
import com.scanms.product.repository.SearchEventRepository;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:search-history;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop", "scanms.catalog.seed-enabled=false", "scanms.catalog.sora-seed-enabled=false", "scanms.live.lifecycle.enabled=false", "logging.level.root=WARN"})
class SearchHistoryIntegrationTest {
    @Autowired
    SearchHistoryService service;
    @Autowired
    SearchEventRepository repository;
    @MockitoBean
    JwtDecoder decoder;
    private final Jwt alice = jwt("alice"), bob = jwt("bob");

    private static Jwt jwt(String id) {
        return Jwt.withTokenValue(id).header("alg", "RS256").subject(id).build();
    }

    private SearchEventRequest request(String id) {
        return new SearchEventRequest(id, "guest-session-123456", "  giày   trắng  ", 2, Map.of("category", "shoes", "accessToken", "never-store-this"));
    }

    @BeforeEach
    void clear() {
        repository.deleteAll();
    }

    @Test
    void recordsOnceNormalizesQueryAndSeparatesUsersAndGuests() {
        var first = service.record(request("same"), alice);
        assertEquals(first.eventId(), service.record(request("same"), alice).eventId());
        service.record(request("same"), bob);
        service.record(request("same"), null);
        assertEquals(3, repository.count());
        assertEquals("giày trắng", service.recent(alice).getFirst().keyword());
        assertEquals(1, service.recent(bob).size());
        assertTrue(repository.findAll().stream().allMatch(event -> event.getActorHash().length() == 64 && !event.getFilters().containsKey("accessToken")));
        service.clear(alice);
        assertTrue(service.recent(alice).isEmpty());
        assertEquals(1, service.recent(bob).size());
        assertThrows(RuntimeException.class, () -> service.recent(null));
    }

    @Test
    void retentionRemovesOnlyExpiredEvents() {
        service.record(request("old"), alice);
        service.record(request("new"), bob);
        var old = repository.findAll().stream().filter(event -> event.getRequestId().equals("old")).findFirst().orElseThrow();
        old.setCreatedAt(Instant.now().minusSeconds(31L * 86400));
        repository.saveAndFlush(old);
        ((com.scanms.product.service.impl.SearchHistoryServiceImpl) service).cleanup();
        assertEquals(1, repository.count());
        assertEquals("new", repository.findAll().getFirst().getRequestId());
    }
}
