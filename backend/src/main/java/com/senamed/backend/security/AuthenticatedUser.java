package com.senamed.backend.security;

/**
 * Security principal extracted from a validated JWT. Populated by
 * {@link JwtAuthenticationFilter} and set as the {@link org.springframework.security.core.Authentication}
 * principal, so any layer of the app can resolve "who is calling and from which clinic" without
 * hitting the database again.
 */
public record AuthenticatedUser(Long userId, Long clinicId, String email, String role, Long doctorId) {
}
