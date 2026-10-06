package com.scanms.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.jwt.*;

/** Explicit deployment profile: the broker verifies existing commerce sessions before minting short-lived identities. */
@Configuration @Profile("commerce-bridge")
public class CommerceBridgeIdentityConfig {
    @Bean JwtDecoder commerceJwtDecoder(@Value("${scanms.commerce.issuer:http://127.0.0.1:3302}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        OAuth2TokenValidator<Jwt> audience = jwt -> jwt.getAudience().contains("scanms-finance")
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Finance audience required", null));
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(issuer), audience));
        return decoder;
    }
}
