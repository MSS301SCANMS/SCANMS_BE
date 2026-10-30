package com.scanms.ops;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LogMaskerTest {
    @Test
    void masksJwtAndBearerTokens() {
        String jwt = "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiIxMjM0NTYifQ.c2lnbmF0dXJlMTIz";
        assertFalse(LogMasker.mask("token " + jwt + " ok").contains("eyJ"));
        assertEquals("Authorization: Bearer [token]", LogMasker.mask("Authorization: Bearer abcdefgh12345678"));
    }

    @Test
    void masksSecretsInKeyValueForms() {
        assertEquals("password=*** and apiKey: ***", LogMasker.mask("password=hunter2 and apiKey: \"abc123\""));
        assertTrue(LogMasker.mask("checksum-key=deadbeef").endsWith("***"));
        assertTrue(LogMasker.mask("client_secret=xyz,next").contains("***,next"));
    }

    @Test
    void masksEmailsButKeepsDomain() {
        assertEquals("t***@gmail.com da dang nhap", LogMasker.mask("thanh.nguyen@gmail.com da dang nhap"));
    }

    @Test
    void leavesNormalTextAndNullAlone() {
        assertEquals("Da dam bao vi CUSTOMER cho userId=8bda8c4c", LogMasker.mask("Da dam bao vi CUSTOMER cho userId=8bda8c4c"));
        assertNull(LogMasker.mask(null));
        assertEquals("", LogMasker.mask(""));
    }
}
