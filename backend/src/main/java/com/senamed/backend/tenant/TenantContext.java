package com.senamed.backend.tenant;

import com.senamed.backend.common.DoctorAccessRequiredException;
import com.senamed.backend.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Static accessor for the "current tenant" (clinic) of the authenticated request.
 *
 * <p>The JWT carries {@code clinicId}, {@code userId} and {@code role}. Once
 * {@code JwtAuthenticationFilter} validates the token it stores an {@link AuthenticatedUser}
 * as the {@link Authentication} principal, and every downstream layer (services, interceptors,
 * repositories) can resolve the current tenant through this class instead of re-parsing the
 * token or trusting client-supplied ids.</p>
 */
public final class TenantContext {

    private TenantContext() {
    }

    public static AuthenticatedUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new IllegalStateException("No authenticated tenant user found in the security context");
        }
        return user;
    }

    /**
     * @return the clinic id of the currently authenticated user. Every query/service touching
     * tenant-scoped data (e.g. future {@code doctors}, {@code appointments} entities) must filter
     * by this value.
     */
    public static Long currentClinicId() {
        return currentUser().clinicId();
    }

    /**
     * @return the doctor id of the currently authenticated user.
     * @throws DoctorAccessRequiredException if the caller has no doctorId claim (e.g. an ADMIN
     * token calling a {@code /api/doctors/me/**} endpoint) - mapped to 403 by
     * {@code GlobalExceptionHandler}, not a NullPointerException.
     */
    public static Long currentDoctorId() {
        Long doctorId = currentUser().doctorId();
        if (doctorId == null) {
            throw new DoctorAccessRequiredException();
        }
        return doctorId;
    }

    public static boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser;
    }
}
