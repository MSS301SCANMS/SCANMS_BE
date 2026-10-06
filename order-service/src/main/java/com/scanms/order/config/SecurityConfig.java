package com.scanms.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http.csrf(csrf -> csrf.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/swagger-ui/**", "/swagger-ui.html",
                                "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/webjars/**", "/actuator/health", "/error",
                                "/api/v1/orders/checkout", "/api/v1/orders/guest-checkout",
                                "/api/v1/orders/track", "/api/v1/orders/**",
                                "/api/v1/seller-orders/**", "/api/v1/shipments/**",
                                "/api/v1/returns/**", "/api/v1/discount-allocations/**")
                        .permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()))
                .build();
    }
}
