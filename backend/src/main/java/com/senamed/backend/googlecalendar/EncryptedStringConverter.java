package com.senamed.backend.googlecalendar;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/**
 * Encrypts a single string column at rest (AES-256-GCM) - used for
 * {@code DoctorGoogleCalendarCredential.refreshToken} (KAN-78). No column-level encryption
 * precedent existed in this codebase before this - greenfield, mirroring the "env var master
 * secret, dev-only default, must be overridden in production" posture already used for
 * {@code senamed.jwt.secret}. The configured key string (any length) is SHA-256-hashed into a
 * fixed 256-bit AES key, so the property doesn't need to be a precisely-sized Base64 value.
 *
 * <p>Storage format: {@code base64(iv[12] || ciphertext+tag)} - a fresh random IV per encryption,
 * prefixed to the ciphertext so decryption is self-contained.</p>
 *
 * <p>{@code @Component} makes this a Spring-managed bean; Spring Boot's Hibernate
 * autoconfiguration wires {@code @Converter} classes that are also Spring beans through its own
 * bean container, so {@code @Value} injection works here despite JPA normally instantiating
 * converters itself.</p>
 */
@Converter
@Component
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String TRANSFORMATION = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    private final SecretKeySpec key;

    public EncryptedStringConverter(@Value("${senamed.google.token-encryption-key}") String configuredKey) {
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            this.key = new SecretKeySpec(sha256.digest(configuredKey.getBytes(StandardCharsets.UTF_8)), "AES");
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 is not available", ex);
        }
    }

    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            byte[] iv = new byte[GCM_IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] ciphertext = cipher.doFinal(attribute.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);
            return Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to encrypt column value", ex);
        }
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        try {
            byte[] combined = Base64.getDecoder().decode(dbData);
            byte[] iv = Arrays.copyOfRange(combined, 0, GCM_IV_LENGTH_BYTES);
            byte[] ciphertext = Arrays.copyOfRange(combined, GCM_IV_LENGTH_BYTES, combined.length);

            Cipher cipher = Cipher.getInstance(TRANSFORMATION);
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to decrypt column value", ex);
        }
    }
}
