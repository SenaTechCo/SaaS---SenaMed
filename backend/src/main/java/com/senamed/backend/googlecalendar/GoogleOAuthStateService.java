package com.senamed.backend.googlecalendar;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;

/**
 * Signs/verifies the OAuth {@code state} parameter correlating a Google consent-screen callback
 * back to the doctor who initiated it (KAN-78). The callback endpoint is necessarily public (it's
 * a browser redirect coming from Google, carrying no SenaMed JWT), so {@code TenantContext} isn't
 * available there - this compact, short-lived HMAC-signed token is the substitute, in the same
 * spirit as the {@code UUID} tokens already used for {@code cancelToken}/{@code confirmationToken}.
 * Reuses {@code senamed.jwt.secret} rather than introducing a second signing secret - it already
 * serves as this app's general-purpose HMAC key, not something JWT-specific in nature.
 */
@Component
public class GoogleOAuthStateService {

    private static final long VALIDITY_SECONDS = 10 * 60; // 10 minutes

    private final SecretKeySpec key;

    public GoogleOAuthStateService(@Value("${senamed.jwt.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    public String sign(Long doctorId) {
        long expiresAt = Instant.now().getEpochSecond() + VALIDITY_SECONDS;
        String payload = doctorId + ":" + expiresAt;
        String encodedPayload = base64UrlEncode(payload.getBytes(StandardCharsets.UTF_8));
        String signature = base64UrlEncode(hmac(encodedPayload));
        return encodedPayload + "." + signature;
    }

    /** @return the doctor id if {@code state} is well-formed, correctly signed and not expired; empty otherwise. */
    public Optional<Long> verify(String state) {
        if (state == null) {
            return Optional.empty();
        }
        int dot = state.indexOf('.');
        if (dot < 0) {
            return Optional.empty();
        }

        String encodedPayload = state.substring(0, dot);
        String signature = state.substring(dot + 1);

        String expectedSignature = base64UrlEncode(hmac(encodedPayload));
        if (!MessageDigest.isEqual(
                signature.getBytes(StandardCharsets.UTF_8), expectedSignature.getBytes(StandardCharsets.UTF_8))) {
            return Optional.empty();
        }

        try {
            String payload = new String(Base64.getUrlDecoder().decode(encodedPayload), StandardCharsets.UTF_8);
            String[] parts = payload.split(":", 2);
            long doctorId = Long.parseLong(parts[0]);
            long expiresAt = Long.parseLong(parts[1]);
            if (Instant.now().getEpochSecond() > expiresAt) {
                return Optional.empty();
            }
            return Optional.of(doctorId);
        } catch (RuntimeException ex) {
            return Optional.empty();
        }
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to sign OAuth state", ex);
        }
    }

    private static String base64UrlEncode(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
