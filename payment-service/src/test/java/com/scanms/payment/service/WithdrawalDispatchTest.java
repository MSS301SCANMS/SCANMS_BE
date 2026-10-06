package com.scanms.payment.service;

import com.scanms.payment.entity.WithdrawalRequest;
import com.scanms.payment.repository.WithdrawalRequestRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.test.util.ReflectionTestUtils;
import com.scanms.payment.config.SecurityConfig;
import java.time.Instant;
import java.util.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

class WithdrawalDispatchTest {
    @Test void workerUsesVerifiedServiceRoleAndRestoresPreviousContext() {
        var credentials=mock(FinanceOutbox.class); var decoder=mock(JwtDecoder.class);
        var repository=mock(WithdrawalRequestRepository.class); var processor=mock(WithdrawalProcessor.class); var provider=mock(PayosProvider.class);
        var worker=new WithdrawalDispatch(credentials,decoder,new SecurityConfig().jwtConverter(),repository,processor,provider);
        ReflectionTestUtils.setField(worker,"enabled",true); when(provider.payoutAvailable()).thenReturn(true);
        when(repository.findTop20ByStatusInAndScheduledForLessThanEqualOrderByScheduledForAsc(anyCollection(),any())).thenReturn(List.of(WithdrawalRequest.builder().withdrawalId("due").build()));
        when(credentials.serviceToken()).thenReturn(Optional.of("service-token"));
        when(decoder.decode("service-token")).thenReturn(Jwt.withTokenValue("service-token").header("alg","RS256").subject("service-account").issuedAt(Instant.now())
                .claim("realm_access",Map.of("roles",List.of("PAYMENT_INTERNAL"))).build());
        var previous=SecurityContextHolder.getContext();
        doAnswer(call -> { assertEquals("service-account",SecurityContextHolder.getContext().getAuthentication().getName()); return null; }).when(processor).execute("due");
        worker.processDue(); verify(processor).execute("due"); assertSame(previous,SecurityContextHolder.getContext());
    }
    @Test void missingOrWrongServiceRoleCannotTransfer() {
        var credentials=mock(FinanceOutbox.class); var decoder=mock(JwtDecoder.class);
        var repository=mock(WithdrawalRequestRepository.class); var processor=mock(WithdrawalProcessor.class); var provider=mock(PayosProvider.class);
        var worker=new WithdrawalDispatch(credentials,decoder,new SecurityConfig().jwtConverter(),repository,processor,provider);
        ReflectionTestUtils.setField(worker,"enabled",true); when(provider.payoutAvailable()).thenReturn(true);
        when(repository.findTop20ByStatusInAndScheduledForLessThanEqualOrderByScheduledForAsc(anyCollection(),any())).thenReturn(List.of(WithdrawalRequest.builder().withdrawalId("due").build()));
        when(credentials.serviceToken()).thenReturn(Optional.empty()); worker.processDue(); verifyNoInteractions(decoder,processor);
        when(credentials.serviceToken()).thenReturn(Optional.of("user-token"));
        when(decoder.decode("user-token")).thenReturn(Jwt.withTokenValue("user-token").header("alg","RS256").subject("customer").claim("realm_access",Map.of("roles",List.of("USER"))).build());
        worker.processDue(); verifyNoInteractions(processor);
    }
}
