package com.scanms.user.service;

import com.scanms.user.constant.RoleName;
import com.scanms.user.dto.request.RegisterRequest;
import com.scanms.user.entity.Role;
import com.scanms.user.entity.User;
import com.scanms.user.event.UserRegisteredEvent;
import com.scanms.user.repository.RoleRepository;
import com.scanms.user.repository.UserRepository;
import com.scanms.user.service.impl.AuthServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class AuthRegistrationTest {
    private static final String BASE = "https://identity.example.test";
    private static final String USERS = BASE + "/admin/realms/fixture/users";
    private final UserRepository users = mock(UserRepository.class);
    private final RoleRepository roles = mock(RoleRepository.class);
    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private AuthServiceImpl service;
    private MockRestServiceServer server;

    @BeforeEach
    void setup() {
        service = new AuthServiceImpl(users, roles, mock(OtpService.class), mock(MailService.class), events);
        ReflectionTestUtils.setField(service, "serverUrl", BASE);
        ReflectionTestUtils.setField(service, "realm", "fixture");
        ReflectionTestUtils.setField(service, "clientId", "fixture-client");
        ReflectionTestUtils.setField(service, "clientSecret", "fixture-secret");
        server = MockRestServiceServer.createServer((RestTemplate) ReflectionTestUtils.getField(service, "restTemplate"));
        when(roles.findAllByNameIn(List.of(RoleName.USER))).thenReturn(Set.of(new Role()));
        when(users.saveAndFlush(any(User.class))).thenAnswer(call -> {
            User user = call.getArgument(0);
            user.setUserId("local-buyer");
            return user;
        });
    }

    private RegisterRequest request() {
        return new RegisterRequest(" buyer ", " Buyer@Example.test ", "fixture-password", " Tú ", " Trương ");
    }

    private void token() {
        server.expect(requestTo(BASE + "/realms/fixture/protocol/openid-connect/token"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("{\"access_token\":\"admin-token\"}", MediaType.APPLICATION_JSON));
    }

    private void create() {
        create(false);
    }

    private void create(boolean verifyEmail) {
        token();
        server.expect(requestTo(USERS)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.username").value("buyer"))
                .andExpect(jsonPath("$.email").value("buyer@example.test"))
                .andExpect(jsonPath("$.firstName").value("Tú"))
                .andExpect(jsonPath("$.credentials[0].value").value("fixture-password"))
                .andExpect(jsonPath("$.emailVerified").value(!verifyEmail))
                .andExpect(jsonPath("$.requiredActions.length()").value(verifyEmail ? 1 : 0))
                .andRespond(withStatus(HttpStatus.CREATED).location(URI.create(USERS + "/new-subject")));
    }

    private void grantRole() {
        server.expect(requestTo(BASE + "/admin/realms/fixture/roles/USER"))
                .andRespond(withSuccess("{\"id\":\"role-user\",\"name\":\"USER\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(USERS + "/new-subject/role-mappings/realm"))
                .andExpect(method(HttpMethod.POST)).andExpect(jsonPath("$[0].name").value("USER"))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    private void cleanup() {
        server.expect(requestTo(USERS + "/new-subject")).andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NO_CONTENT));
    }

    @Test
    void createsCustomerProfileAndPublishesEventOnlyAfterSaving() {
        create();
        grantRole();
        service.register(request());
        verify(users).saveAndFlush(argThat(user -> "new-subject".equals(user.getIdentitySubject())
                && "buyer@example.test".equals(user.getEmail()) && "Trương Tú".equals(user.getFullName())));
        verify(roles).findAllByNameIn(List.of(RoleName.USER));
        verify(events).publishEvent(any(UserRegisteredEvent.class));
        server.verify();
    }

    @Test
    void emailVerificationIsAddedOnlyWhenExplicitlyEnabled() {
        ReflectionTestUtils.setField(service, "emailVerificationEnabled", true);
        create(true);
        server.expect(requestTo(USERS + "/new-subject/send-verify-email"))
                .andExpect(method(HttpMethod.PUT)).andRespond(withStatus(HttpStatus.NO_CONTENT));
        grantRole();
        service.register(request());
        verify(users).saveAndFlush(any(User.class));
        server.verify();
    }

    @Test
    void duplicateEmailIs409WithoutProviderDetailsOrDeletingAnExistingAccount() {
        token();
        server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CONFLICT)
                .contentType(MediaType.APPLICATION_JSON).body("{\"errorMessage\":\"User exists with same email\"}"));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(request()));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Email đã được sử dụng"));
        assertFalse(error.getReason().contains(BASE));
        verifyNoInteractions(users, events);
        server.verify();
    }

    @Test
    void duplicateUsernameIs409() {
        token();
        server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CONFLICT)
                .body("{\"errorMessage\":\"User exists with same username\"}"));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(request()));
        assertEquals(409, error.getStatusCode().value());
        assertTrue(error.getReason().contains("Tên đăng nhập đã tồn tại"));
        verifyNoInteractions(users, events);
        server.verify();
    }

    @Test
    void invalidClientIs503WithoutExposingSecretOrProviderBody() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-provider-detail"));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(request()));
        assertEquals(503, error.getStatusCode().value());
        assertFalse(error.getReason().contains("private-provider-detail"));
        verifyNoInteractions(users, events);
        server.verify();
    }

    @Test
    void invalidRegistrationIs400() {
        token();
        server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.BAD_REQUEST).body("private-password-policy"));
        var error = assertThrows(ResponseStatusException.class, () -> service.register(request()));
        assertEquals(400, error.getStatusCode().value());
        assertFalse(error.getReason().contains("private-password-policy"));
        verifyNoInteractions(users, events);
    }

    @Test
    void failedRoleAssignmentCompensatesOnlyTheNewIdentityAndDoesNotSave() {
        create();
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        cleanup();
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.register(request())).getStatusCode().value());
        verifyNoInteractions(users, events);
        server.verify();
    }

    @Test
    void failedDatabaseSaveDoesNotLeaveANewKeycloakAccountBehind() {
        create();
        grantRole();
        cleanup();
        when(users.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("private-sql"));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.register(request())).getStatusCode().value());
        verifyNoInteractions(events);
        server.verify();
    }

    @Test
    void missingCreatedIdentityCannotReportSuccess() {
        token();
        server.expect(requestTo(USERS)).andRespond(withStatus(HttpStatus.CREATED));
        server.expect(requestTo(USERS + "?username=buyer&exact=true"))
                .andRespond(withSuccess("[]", MediaType.APPLICATION_JSON));
        assertEquals(503, assertThrows(ResponseStatusException.class, () -> service.register(request())).getStatusCode().value());
        verifyNoInteractions(users, events);
        server.verify();
    }

    @Test
    void transactionRollbackTriggersCompensation() {
        create();
        grantRole();
        cleanup();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.register(request());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
            server.verify();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void transactionCommitKeepsTheCreatedAccount() {
        create();
        grantRole();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.register(request());
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCompletion(TransactionSynchronization.STATUS_COMMITTED));
            server.verify();
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }
}
