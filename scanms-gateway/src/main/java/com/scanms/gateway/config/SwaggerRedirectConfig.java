package com.scanms.gateway.config;

import java.net.URI;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerResponse;

@Configuration
public class SwaggerRedirectConfig {

    @Bean
    RouterFunction<ServerResponse> swaggerRedirectRoute() {
        return RouterFunctions.route()
                .GET("/", request -> ServerResponse.temporaryRedirect(URI.create("/swagger-ui.html")).build())
                .build();
    }
}
