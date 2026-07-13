package com.senamed.backend.tenant;

import jakarta.persistence.EntityManager;
import org.hibernate.Session;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Enables the Hibernate {@link TenantScopedEntity#TENANT_FILTER} for the duration of an
 * authenticated HTTP request, scoped to the caller's clinic. See {@link TenantScopedEntity} for
 * the full explanation of this pattern.
 *
 * <p>Runs as a {@link HandlerInterceptor} (after Spring Security's filter chain has already
 * populated the {@link org.springframework.security.core.context.SecurityContext}), so
 * {@link TenantContext#isAuthenticated()} reliably reflects whether the request carried a valid
 * JWT.</p>
 */
@Component
public class TenantFilterInterceptor implements HandlerInterceptor {

    private final EntityManager entityManager;

    public TenantFilterInterceptor(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (TenantContext.isAuthenticated()) {
            Session session = entityManager.unwrap(Session.class);
            // The filter is only registered by Hibernate once at least one mapped entity extends
            // TenantScopedEntity. Today (Fase 1) only `users` is tenant-scoped and it deliberately
            // does not use this mechanism (see User's javadoc), so the filter may not exist yet.
            // Guard here so the foundation is a no-op until a future entity (e.g. Doctor) opts in.
            if (session.getSessionFactory().getDefinedFilterNames().contains(TenantScopedEntity.TENANT_FILTER)) {
                session.enableFilter(TenantScopedEntity.TENANT_FILTER)
                        .setParameter("clinicId", TenantContext.currentClinicId());
            }
        }
        return true;
    }
}
