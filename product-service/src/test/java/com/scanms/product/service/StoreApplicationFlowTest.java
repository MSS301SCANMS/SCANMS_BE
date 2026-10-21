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
    @Autowired org.springframework.web.context.WebApplicationContext context;
    @MockitoBean ProductAccess access;
    @MockitoBean ShopRoleGranter roleGranter;
    @MockitoBean JwtDecoder jwtDecoder;

    private static java.util.Map<String, Object> completeDetails() {
        return java.util.Map.ofEntries(
                java.util.Map.entry("businessType", "HOUSEHOLD"), java.util.Map.entry("taxCode", "TEST-TAX"),
                java.util.Map.entry("idCardNumber", "000000000001"),
                java.util.Map.entry("frontCardUrl", "https://example.test/front.png"),
                java.util.Map.entry("backCardUrl", "https://example.test/back.png"),
                java.util.Map.entry("businessLicenseUrl", "https://example.test/license.png"),
                java.util.Map.entry("warehouseAddress", "Fixture warehouse"),
                java.util.Map.entry("contactPhone", "0900000000"),
                java.util.Map.entry("contactEmail", "fixture@example.test"),
                java.util.Map.entry("bankName", "Fixture bank"),
                java.util.Map.entry("bankAccountNumber", "0000000001"),
                java.util.Map.entry("bankAccountName", "FIXTURE OWNER"));
    }

    @BeforeEach void reset() {
        applications.deleteAll();
        stores.deleteAll();
        when(access.userId()).thenReturn("owner-1");
    }

    @Test void approvalCreatesOneApprovedStoreAndIgnoresClientStatusAndApplicant() {
        var input = new CreateStoreApplicationRequest("forged-user", "Test Shop", "description",
                StoreApplicationStatus.APPROVED, "forged-reviewer", "forged-note", null, null, completeDetails());
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
        var input = new CreateStoreApplicationRequest(null, "Rejected Shop", null, null, null, null, null, null, completeDetails());
        var pending = service.create(input);
        when(access.userId()).thenReturn("manager-1");
        var rejected = service.review(pending.applicationId(), false, "Incomplete");
        assertEquals(StoreApplicationStatus.REJECTED, rejected.status());
        assertTrue(stores.findAll().isEmpty());
        verifyNoInteractions(roleGranter);
    }

    @Test void failedRoleGrantRollsBackApprovalAndStoreCreation() {
        var input = new CreateStoreApplicationRequest(null, "Retry Shop", null, null, null, null, null, null, completeDetails());
        var pending = service.create(input);
        doThrow(new AppException(com.scanms.product.exception.ErrorCode.CONFLICT))
                .when(roleGranter).grant("owner-1");
        assertThrows(AppException.class, () -> service.review(pending.applicationId(), true, null));
        assertEquals(StoreApplicationStatus.PENDING, applications.findById(pending.applicationId()).orElseThrow().getStatus());
        assertTrue(stores.findAll().isEmpty());
    }

    @Test void shopFormJsonSavesDocumentsBankAndContactDetailsAndReturnsPendingStatus() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        String json = """
                {"proposedStoreName":"Fixture shop","description":"Fixture description","onboardingData":{
                  "shopName":"Fixture shop","warehouseAddress":"Fixture warehouse","businessType":"HOUSEHOLD",
                  "taxCode":"TEST-TAX","contactPhone":"0900000000","contactEmail":"fixture@example.test",
                  "idCardNumber":"000000000001","frontCardUrl":"https://example.test/front.png",
                  "backCardUrl":"https://example.test/back.png","businessLicenseUrl":"https://example.test/license.png",
                  "brandAuthorizationUrl":"https://example.test/brand.png","bankName":"Fixture bank",
                  "bankAccountNumber":"0000000001","bankAccountName":"FIXTURE OWNER"}}
                """;
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/store-applications")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.status").value("PENDING"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.onboardingData.businessLicenseUrl").value("https://example.test/license.png"));
        var saved = applications.findAll().getFirst();
        assertEquals("Fixture shop", saved.getProposedStoreName());
        assertEquals("owner-1", saved.getApplicantUserId());
        assertEquals("0000000001", saved.getOnboardingData().get("bankAccountNumber"));
        assertEquals("https://example.test/back.png", saved.getOnboardingData().get("backCardUrl"));
        assertEquals(14, saved.getOnboardingData().size());
        assertEquals(saved.getOnboardingData(), service.findAll().getFirst().onboardingData());
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/store-applications/" + saved.getApplicationId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.onboardingData.frontCardUrl").value("https://example.test/front.png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.onboardingData.backCardUrl").value("https://example.test/back.png"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.onboardingData.bankAccountNumber").value("0000000001"));
        assertTrue(stores.findAll().isEmpty());
        verifyNoInteractions(roleGranter);
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/store-applications")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isConflict());
        assertEquals(1, applications.count());
    }

    @Test void managerSeesApplicantsAndUsesApproveEndpointWhileApplicantCannotReviewOthers() throws Exception {
        var first = service.create(new CreateStoreApplicationRequest(null, "First shop", null, null, null, null, null, null, completeDetails()));
        when(access.userId()).thenReturn("owner-2");
        var second = service.create(new CreateStoreApplicationRequest(null, "Second shop", null, null, null, null, null, null, completeDetails()));
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        doThrow(new AppException(com.scanms.product.exception.ErrorCode.FORBIDDEN)).when(access).requireManager();
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/store-applications")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.length()").value(1))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result[0].applicationId").value(second.applicationId()));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/store-applications/" + first.applicationId() + "/approve")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isForbidden());
        doNothing().when(access).requireManager();
        when(access.isManager()).thenReturn(true); when(access.userId()).thenReturn("manager-1");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/store-applications")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt()))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.length()").value(2));
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/store-applications/" + first.applicationId() + "/approve")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content("{\"note\":\"Verified documents\"}"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath("$.result.status").value("APPROVED"));
        assertEquals(1, stores.findByOwnerUserId("owner-1").size()); verify(roleGranter).grant("owner-1");
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/api/v1/store-applications"))
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isUnauthorized());
    }

    @Test void applicantCanSupplementOwnPendingApplicationButCannotChangeReviewOrEditAnotherAccount() {
        var legacy = applications.saveAndFlush(com.scanms.product.entity.StoreApplication.builder()
                .applicantUserId("owner-1").proposedStoreName("Old form")
                .status(StoreApplicationStatus.PENDING).submittedAt(java.time.Instant.now()).build());
        var pending = service.getById(legacy.getApplicationId());
        var details = completeDetails();
        var edit = new CreateStoreApplicationRequest("forged-owner", "Updated name", "Description", StoreApplicationStatus.APPROVED, "fake-manager", "fake-note", null, null, details);
        when(access.userId()).thenReturn("someone-else");
        assertThrows(AppException.class, () -> service.updatePending(pending.applicationId(), edit));
        when(access.userId()).thenReturn("owner-1");
        var updated = service.updatePending(pending.applicationId(), edit);
        assertEquals("Updated name", updated.proposedStoreName()); assertEquals(details, updated.onboardingData());
        assertEquals("owner-1", updated.applicantUserId()); assertEquals(StoreApplicationStatus.PENDING, updated.status());
        assertNull(updated.reviewedByUserId()); assertNull(updated.reviewNote()); assertEquals(1, applications.count());
        when(access.userId()).thenReturn("manager-1"); service.review(pending.applicationId(), false, "Missing details");
        when(access.userId()).thenReturn("owner-1");
        assertThrows(AppException.class, () -> service.updatePending(pending.applicationId(), edit));
    }

    @Test void newApplicationsCannotBeSavedWithMissingBlankOrInvalidDetails() throws Exception {
        var mvc = org.springframework.test.web.servlet.setup.MockMvcBuilders.webAppContextSetup(context)
                .apply(org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity()).build();
        for (String json : java.util.List.of("{\"proposedStoreName\":\"Missing details\"}",
                "{\"proposedStoreName\":\"Empty details\",\"onboardingData\":{}}")) {
            mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post("/api/v1/store-applications")
                    .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON).content(json))
                    .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.status().isBadRequest());
        }
        for (String field : completeDetails().keySet()) {
            var incomplete = new java.util.HashMap<>(completeDetails()); incomplete.remove(field);
            assertThrows(AppException.class, () -> service.create(new CreateStoreApplicationRequest(null, "Incomplete shop", null,
                    null, null, null, null, null, incomplete)), "Missing field: " + field);
            incomplete.put(field, "   ");
            assertThrows(AppException.class, () -> service.create(new CreateStoreApplicationRequest(null, "Blank field shop", null,
                    null, null, null, null, null, incomplete)), "Blank field: " + field);
        }
        var invalid = new java.util.HashMap<>(completeDetails()); invalid.put("bankAccountNumber", 12345);
        assertThrows(AppException.class, () -> service.create(new CreateStoreApplicationRequest(null, "Wrong type", null,
                null, null, null, null, null, invalid)));
        assertEquals(0, applications.count()); assertEquals(0, stores.count()); verifyNoInteractions(roleGranter);
    }

    @Test void invalidUpdateCannotErasePreviouslySavedDocuments() {
        var pending = service.create(new CreateStoreApplicationRequest(null, "Complete shop", null,
                null, null, null, null, null, completeDetails()));
        var incomplete = new java.util.HashMap<>(completeDetails()); incomplete.remove("frontCardUrl");
        for (var data : java.util.Arrays.asList(null, incomplete)) {
            assertThrows(AppException.class, () -> service.updatePending(pending.applicationId(), new CreateStoreApplicationRequest(
                    null, "Changed name", "Changed description", null, null, null, null, null, data)));
        }
        var stillSaved = service.getById(pending.applicationId());
        assertEquals("Complete shop", stillSaved.proposedStoreName());
        assertEquals(completeDetails(), stillSaved.onboardingData());
        assertEquals(1, applications.count());
    }

    @Test void managerCannotApproveLegacyApplicationUntilItsDocumentsAreSupplemented() {
        var legacy = applications.saveAndFlush(com.scanms.product.entity.StoreApplication.builder()
                .applicantUserId("owner-1").proposedStoreName("Legacy shop")
                .status(StoreApplicationStatus.PENDING).submittedAt(java.time.Instant.now()).build());
        when(access.userId()).thenReturn("manager-1");
        assertThrows(AppException.class, () -> service.review(legacy.getApplicationId(), true, null));
        assertEquals(StoreApplicationStatus.PENDING, applications.findById(legacy.getApplicationId()).orElseThrow().getStatus());
        assertTrue(stores.findAll().isEmpty()); verifyNoInteractions(roleGranter);
        when(access.userId()).thenReturn("owner-1");
        service.updatePending(legacy.getApplicationId(), new CreateStoreApplicationRequest(null, "Completed shop", null,
                null, null, null, null, null, completeDetails()));
        when(access.userId()).thenReturn("manager-1");
        assertEquals(StoreApplicationStatus.APPROVED, service.review(legacy.getApplicationId(), true, "Checked").status());
        assertEquals(completeDetails(), applications.findById(legacy.getApplicationId()).orElseThrow().getOnboardingData());
    }

    @Test void multipleNewApplicantsKeepTheirOwnDetailsWhenManagerReadsThemLater() {
        var expected = new java.util.HashMap<String, java.util.Map<String, Object>>();
        for (int i = 1; i <= 3; i++) {
            String owner = "new-owner-" + i;
            when(access.userId()).thenReturn(owner);
            var details = new java.util.HashMap<>(completeDetails());
            details.put("bankAccountNumber", "000000000" + i);
            details.put("frontCardUrl", "https://example.test/owner-" + i + "-front.png");
            var saved = service.create(new CreateStoreApplicationRequest(null, "New shop " + i, null,
                    null, null, null, null, null, details));
            expected.put(saved.applicationId(), details);
            assertEquals(details, service.getById(saved.applicationId()).onboardingData());
            assertEquals(1, service.findAll().size());
        }
        when(access.userId()).thenReturn("manager-1"); when(access.isManager()).thenReturn(true);
        var managerRecords = service.findAll();
        assertEquals(3, managerRecords.size());
        for (var row : managerRecords) {
            assertEquals(expected.get(row.applicationId()), row.onboardingData());
            assertEquals(expected.get(row.applicationId()), service.getById(row.applicationId()).onboardingData());
        }
    }
}
