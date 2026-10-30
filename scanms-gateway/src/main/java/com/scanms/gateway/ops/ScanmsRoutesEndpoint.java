package com.scanms.gateway.ops;

import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code /actuator/scanmsroutes}: danh sách route của Gateway cho trang Giám sát hệ thống. Chỉ lộ id, điều kiện
 * đường dẫn và {@code host:port} đích, không có filter hay metadata. Quyền ADMIN/SYSTEM_ADMIN được kiểm tra ở
 * {@code SecurityConfig} vì Gateway là ứng dụng WebFlux.
 */
@Endpoint(id = "scanmsroutes")
public class ScanmsRoutesEndpoint {
    private final RouteLocator routes;
    private final Environment env;

    public ScanmsRoutesEndpoint(RouteLocator routes, Environment env) {
        this.routes = routes;
        this.env = env;
    }

    @ReadOperation
    public Map<String, Object> routes() {
        List<Route> all = routes.getRoutes().collectList().block(Duration.ofSeconds(3));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (all != null) {
            for (Route route : all) {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("id", route.getId());
                row.put("order", route.getOrder());
                row.put("predicate", String.valueOf(route.getPredicate()));
                row.put("target", route.getUri().getHost() == null ? route.getUri().toString()
                        : route.getUri().getHost() + (route.getUri().getPort() > 0 ? ":" + route.getUri().getPort() : ""));
                rows.add(row);
            }
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("count", rows.size());
        body.put("routes", rows);
        body.put("jwtIssuer", env.getProperty("spring.security.oauth2.resourceserver.jwt.issuer-uri"));
        return body;
    }
}
