package com.scanms.user.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class ProfileIntegrityErrorTest {
    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();
    @Test void longProfileFieldIsNotAnIdentityConflict() {
        var response = handler.handleConflict(new DataIntegrityViolationException("storage rejected",new SQLException("too long","22001")));
        assertEquals(400,response.getStatusCode().value());
        assertFalse(response.getBody().message().contains("Identity conflicts"));
    }
    @Test void uniqueIdentityStillReturnsConflict() {
        var response = handler.handleConflict(new DataIntegrityViolationException("duplicate",new SQLException("duplicate","23505")));
        assertEquals(409,response.getStatusCode().value());
        assertTrue(response.getBody().message().contains("Identity conflicts"));
    }
}
