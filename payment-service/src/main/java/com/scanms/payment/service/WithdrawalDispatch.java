package com.scanms.payment.service;

import com.scanms.payment.constant.WithdrawalStatus;
import com.scanms.payment.repository.WithdrawalRequestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.stereotype.Service;
import java.time.Instant;
import java.util.List;

/** Uses a genuine issuer-verified service-account token; never impersonates an administrator. */
@Service @RequiredArgsConstructor
public class WithdrawalDispatch {
    private final FinanceOutbox credentials;
    private final JwtDecoder decoder;
    private final JwtAuthenticationConverter converter;
    private final WithdrawalRequestRepository repository;
    private final WithdrawalProcessor processor;
    private final PayosProvider provider;
    @Value("${scanms.payment.automatic-payouts:true}") private boolean enabled;
    @Scheduled(fixedDelayString="${scanms.payment.payout-delay-ms:60000}")
    public void processDue() {
        if(!enabled || !provider.payoutAvailable()) return;
        var due=repository.findTop20ByStatusInAndScheduledForLessThanEqualOrderByScheduledForAsc(
                List.of(WithdrawalStatus.APPROVED,WithdrawalStatus.PROCESSING),Instant.now());
        if(due.isEmpty()) return;
        var token=credentials.serviceToken(); if(token.isEmpty()) return;
        var previous=SecurityContextHolder.getContext();
        try {
            var authentication=converter.convert(decoder.decode(token.get()));
            if(authentication==null || authentication.getAuthorities().stream().noneMatch(a -> "ROLE_PAYMENT_INTERNAL".equals(a.getAuthority()))) return;
            var context=SecurityContextHolder.createEmptyContext(); context.setAuthentication(authentication); SecurityContextHolder.setContext(context);
            for(var request:due) try { processor.execute(request.getWithdrawalId()); } catch(Exception ex) { /* Durable request remains visible for reconciliation. */ }
        } catch(Exception ex) { /* No verified service identity: do not execute any transfers. */ }
        finally { SecurityContextHolder.setContext(previous); }
    }
}
