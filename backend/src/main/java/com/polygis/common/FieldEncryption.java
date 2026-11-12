package com.polygis.common;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * AES-256-GCM for small secrets stored in the database (TOTP seeds). Format: {@code v1:base64(iv|ciphertext|tag)}.
 * Key: {@code APP_ENCRYPTION_KEY} (base64, 32 bytes: {@code openssl rand -base64 32}); if unset it is derived from
 * the JWT secret so existing setups keep working - set a dedicated key in production so rotating one secret does
 * not require the other.
 */
@Component
public class FieldEncryption {

    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    public FieldEncryption(@Value("${app.security.encryption-key:}") String encryptionKey,
                           @Value("${app.security.jwt.secret:}") String jwtSecret) {
        this.key = new SecretKeySpec(deriveKey(encryptionKey, jwtSecret), "AES");
    }

    static byte[] deriveKey(String encryptionKey, String jwtSecret) {
        if (StringUtils.hasText(encryptionKey)) {
            byte[] raw = Base64.getDecoder().decode(encryptionKey.trim());
            if (raw.length != 32) throw new IllegalStateException("app.security.encryption-key must be 32 bytes (base64)");
            return raw;
        }
        if (!StringUtils.hasText(jwtSecret)) {
            throw new IllegalStateException("Set app.security.encryption-key (APP_ENCRYPTION_KEY)");
        }
        try {
            return MessageDigest.getInstance("SHA-256")
                    .digest(("field-encryption:" + jwtSecret).getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String encrypt(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[12];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ct.length).put(iv).put(ct).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    /** Values without the prefix are legacy plaintext and returned unchanged (encrypted on the next save). */
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) return stored;
        try {
            byte[] all = Base64.getDecoder().decode(stored.substring(PREFIX.length()));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, all, 0, 12));
            return new String(cipher.doFinal(all, 12, all.length - 12), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Cannot decrypt stored secret (wrong APP_ENCRYPTION_KEY?)", e);
        }
    }
}
