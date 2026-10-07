package com.scanms.payment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import java.util.*;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/webjars/**", "/actuator/health", "/error", "/api/v1/payments/webhooks/payos", "/api/v1/payouts/webhooks/payos")
                        .permitAll()
                        .requestMatchers(org.springframework.http.HttpMethod.POST,
                                "/api/v1/payments", "/api/v1/bank-accounts", "/api/v1/withdrawals", "/api/v1/settlements", "/api/v1/fee-configs").denyAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtConverter())))
                .build();
    }
    @Bean
    public JwtAuthenticationConverter jwtConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Set<String> roles = new HashSet<>();
            Object realm = jwt.getClaim("realm_access");
            if (realm instanceof Map<?,?> map && map.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
            Object resources = jwt.getClaim("resource_access");
            if (resources instanceof Map<?,?> map) map.values().forEach(v -> {
                if (v instanceof Map<?,?> entry && entry.get("roles") instanceof Collection<?> list) list.forEach(r -> roles.add(r.toString()));
            });
            return roles.stream().map(r -> (org.springframework.security.core.GrantedAuthority)new SimpleGrantedAuthority("ROLE_" + r)).toList();
        });
        return converter;
    }
}
