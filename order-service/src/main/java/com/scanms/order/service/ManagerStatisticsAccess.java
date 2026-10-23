package com.scanms.order.service;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ManagerStatisticsAccess {
    @Value("${clients.user.url:http://localhost:8081}") private String userUrl;
    private final RestClient http = FinanceDispatch.client();

    public void requireManager(Jwt jwt) {
        if (jwt == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Set<String> roles = new HashSet<>();
        collect(jwt.getClaim("realm_access"), roles);
        if (jwt.getClaim("resource_access") instanceof Map<?, ?> resources) resources.values().forEach(value -> collect(value, roles));
        if (Collections.disjoint(roles, Set.of("ADMIN", "MANAGER")))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Chỉ quản trị viên được xem thống kê toàn sàn.");
        try {
            Map<?, ?> envelope = http.get().uri(userUrl + "/api/v1/users")
                    .headers(headers -> headers.setBearerAuth(jwt.getTokenValue())).retrieve().body(Map.class);
            if (envelope == null || !(envelope.get("result") instanceof Map<?, ?> user)
                    || !"ACTIVE".equals(user.get("status")) || !jwt.getSubject().equals(user.get("identitySubject")))
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Tài khoản quản trị chưa được xác minh.");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 401 || ex.getStatusCode().value() == 403)
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Không xác minh được tài khoản quản trị.");
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa xác minh được quyền xem thống kê. Vui lòng thử lại.");
        } catch (RestClientException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Chưa kết nối được dịch vụ tài khoản.");
        }
    }
    private void collect(Object source, Set<String> roles) {
        if (source instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> values)
            values.forEach(value -> roles.add(String.valueOf(value)));
    }
}
