package com.scanms.gateway.filter;

import org.springframework.cloud.gateway.filter.*;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import java.util.UUID;

@Component
public class CorrelationIdFilter implements GlobalFilter, Ordered {
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String value = exchange.getRequest().getHeaders().getFirst("X-Correlation-ID");
        if (value == null || value.isBlank()) value = UUID.randomUUID().toString();
        ServerWebExchange enriched = exchange.mutate().request(
                exchange.getRequest().mutate().header("X-Correlation-ID", value).build()).build();
        return chain.filter(enriched);
    }
    public int getOrder() { return Ordered.HIGHEST_PRECEDENCE; }
}
