package com.senamed.backend.security;

import com.senamed.backend.user.Permission;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Issues and validates the JWTs used by the whole API.
 *
 * <p>Claims layout:</p>
 * <pre>{@code
 * {
 *   "sub": "<userId>",
 *   "clinicId": <clinicId>,
 *   "email": "<email>",
 *   "role": "<ADMIN|DOCTOR|STAFF>",
 *   "doctorId": <doctorId>,   // only present for DOCTOR-role users
 *   "permissions": ["MANAGE_PATIENTS", ...],
 *   "iat": ...,
 *   "exp": ...
 * }
 * }</pre>
 *
 * <p>Carrying {@code clinicId} and {@code role} directly in the token lets
 * {@link JwtAuthenticationFilter} build the full {@link AuthenticatedUser} principal without any
 * extra database round-trip on every request.</p>
 */
@Component
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(
            @Value("${senamed.jwt.secret}") String secret,
            @Value("${senamed.jwt.expiration-minutes}") long expirationMinutes) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    public String generateToken(
            Long userId, Long clinicId, String email, String role, Long doctorId, Set<Permission> permissions) {
        Instant now = Instant.now();
        var builder = Jwts.builder()
                .subject(String.valueOf(userId))
                .claim("clinicId", clinicId)
                .claim("email", email)
                .claim("role", role)
                .claim("permissions", permissions.stream().map(Enum::name).toList());
        if (doctorId != null) {
            builder.claim("doctorId", doctorId);
        }
        return builder
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * @return the decoded principal if the token is structurally valid, correctly signed and not
     * expired; empty otherwise. Never throws - callers should treat "empty" as "unauthenticated".
     */
    public Optional<AuthenticatedUser> parseToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Long userId = Long.valueOf(claims.getSubject());
            Long clinicId = claims.get("clinicId", Number.class).longValue();
            String email = claims.get("email", String.class);
            String role = claims.get("role", String.class);
            Number doctorIdClaim = claims.get("doctorId", Number.class);
            Long doctorId = doctorIdClaim != null ? doctorIdClaim.longValue() : null;
            // Defensive: tokens issued before this claim existed have no "permissions" entry -
            // treat missing/null as an empty set rather than NPE-ing.
            List<?> permissionsClaim = claims.get("permissions", List.class);
            Set<String> permissions = permissionsClaim == null
                    ? Set.of()
                    : new HashSet<>(permissionsClaim.stream().map(String.class::cast).toList());
            return Optional.of(new AuthenticatedUser(userId, clinicId, email, role, doctorId, permissions));
        } catch (JwtException | IllegalArgumentException | NullPointerException e) {
            return Optional.empty();
        }
    }
}
