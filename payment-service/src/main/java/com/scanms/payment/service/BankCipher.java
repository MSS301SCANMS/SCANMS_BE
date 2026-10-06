package com.scanms.payment.service;

import com.scanms.payment.exception.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import javax.crypto.*;
import javax.crypto.spec.*;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Component
public class BankCipher {
    @Value("${scanms.payment.bank-encryption-key:}") private String key;
    private SecretKeySpec key() {
        try {
            byte[] raw = Base64.getDecoder().decode(key);
            if (raw.length != 32) throw new IllegalArgumentException();
            return new SecretKeySpec(raw, "AES");
        } catch (Exception ex) { throw new AppException(ErrorCode.CONFLICT, "BANK_ENCRYPTION_KEY must be a base64 32-byte key"); }
    }
    public String encrypt(String text) {
        try {
            byte[] nonce = new byte[12]; new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key(), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(nonce) + ":" + Base64.getEncoder().encodeToString(encrypted);
        } catch (AppException ex) { throw ex; } catch (Exception ex) { throw new AppException(ErrorCode.INTERNAL_SERVER_ERROR); }
    }
    public String decrypt(String text) {
        try {
            String[] parts = text.split(":"); Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, Base64.getDecoder().decode(parts[0])));
            return new String(cipher.doFinal(Base64.getDecoder().decode(parts[1])), StandardCharsets.UTF_8);
        } catch (AppException ex) { throw ex; } catch (Exception ex) { throw new AppException(ErrorCode.CONFLICT, "Bank destination cannot be decrypted"); }
    }
}
