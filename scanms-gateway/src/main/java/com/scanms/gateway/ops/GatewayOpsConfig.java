package com.scanms.gateway.ops;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration(proxyBeanMethods = false)
public class GatewayOpsConfig {
    @Bean
    ScanmsRoutesEndpoint scanmsRoutesEndpoint(RouteLocator routeLocator, Environment env) {
        return new ScanmsRoutesEndpoint(routeLocator, env);
    }
}
