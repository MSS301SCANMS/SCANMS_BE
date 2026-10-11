package com.scanms.product.service;

import com.scanms.product.constant.StoreApplicationStatus;
import com.scanms.product.constant.StoreStatus;
import com.scanms.product.dto.request.CreateStoreApplicationRequest;
import com.scanms.product.exception.AppException;
import com.scanms.product.repository.StoreApplicationRepository;
import com.scanms.product.repository.StoreRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:store-application-flow;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "logging.level.root=WARN"
})
class StoreApplicationFlowTest {
    @Autowired StoreApplicationService service;
    @Autowired StoreApplicationRepository applications;
    @Autowired StoreRepository stores;
    @MockitoBean ProductAccess access;
    @MockitoBean ShopRoleGranter roleGranter;
    @MockitoBean JwtDecoder jwtDecoder;

    @BeforeEach void reset() {
        applications.deleteAll();
        stores.deleteAll();
        when(access.userId()).thenReturn("owner-1");
    }

    @Test void approvalCreatesOneApprovedStoreAndIgnoresClientStatusAndApplicant() {
        var input = new CreateStoreApplicationRequest("forged-user", "Test Shop", "description",
                StoreApplicationStatus.APPROVED, "forged-reviewer", "forged-note", null, null);
        var pending = service.create(input);
        assertEquals("owner-1", pending.applicantUserId());
        assertEquals(StoreApplicationStatus.PENDING, pending.status());
        assertNull(pending.reviewedByUserId());
        assertThrows(AppException.class, () -> service.create(input));

        when(access.userId()).thenReturn("manager-1");
        var approved = service.review(pending.applicationId(), true, "Verified");
        assertEquals(StoreApplicationStatus.APPROVED, approved.status());
        assertEquals("manager-1", approved.reviewedByUserId());
        assertEquals(1, stores.findByOwnerUserId("owner-1").size());
        var store = stores.findByOwnerUserId("owner-1").getFirst();
        assertEquals(StoreStatus.APPROVED, store.getApprovalStatus());
        assertEquals("Test Shop", store.getName());
        verify(roleGranter).grant("owner-1");
        assertThrows(AppException.class, () -> service.review(pending.applicationId(), true, null));
    }

    @Test void rejectionDoesNotCreateAStore() {
        var input = new CreateStoreApplicationRequest(null, "Rejected Shop", null, null, null, null, null, null);
        var pending = service.create(input);
        when(access.userId()).thenReturn("manager-1");
        var rejected = service.review(pending.applicationId(), false, "Incomplete");
        assertEquals(StoreApplicationStatus.REJECTED, rejected.status());
        assertTrue(stores.findAll().isEmpty());
        verifyNoInteractions(roleGranter);
    }

    @Test void failedRoleGrantRollsBackApprovalAndStoreCreation() {
        var input = new CreateStoreApplicationRequest(null, "Retry Shop", null, null, null, null, null, null);
        var pending = service.create(input);
        doThrow(new AppException(com.scanms.product.exception.ErrorCode.CONFLICT))
                .when(roleGranter).grant("owner-1");
        assertThrows(AppException.class, () -> service.review(pending.applicationId(), true, null));
        assertEquals(StoreApplicationStatus.PENDING, applications.findById(pending.applicationId()).orElseThrow().getStatus());
        assertTrue(stores.findAll().isEmpty());
    }
}
