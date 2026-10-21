package com.scanms.promotion.exception;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import static org.junit.jupiter.api.Assertions.*;

class GlobalExceptionHandlerTest {
    @Test void missingEndpointRemains404RatherThanUnexpected500() {
        var response = new GlobalExceptionHandler().handleMissingEndpoint(
            new NoResourceFoundException(HttpMethod.POST, "api/v1/collaborators/application", "No matching endpoint"));
        assertEquals(404, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertEquals(404, response.getBody().code());
        assertNull(response.getBody().result());
    }
}
