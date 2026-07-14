package com.senamed.backend.tenant;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.clinic.ClinicStatus;
import com.senamed.backend.common.ClinicBlockedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.Set;

/**
 * Enforces RF-022/RN-007: once a clinic's status is {@code PAST_DUE}, {@code BLOCKED} or
 * {@code CANCELLED}, every authenticated endpoint is rejected - except the billing endpoints
 * themselves (excluded via {@code WebMvcConfig}), so a blocked clinic can still pay to unblock
 * itself. {@code TRIAL}/{@code ACTIVE} are never blocked here; {@link com.senamed.backend.billing.ClinicSubscriptionScheduler}
 * is what moves a clinic into a blocked status before this interceptor ever needs to reject it.
 *
 * <p>Registered in {@code WebMvcConfig} to run <em>before</em> {@link TenantFilterInterceptor}, so a
 * rejected request never bothers enabling the Hibernate tenant filter.</p>
 */
@Component
public class ClinicStatusInterceptor implements HandlerInterceptor {

    private static final Set<ClinicStatus> BLOCKED_STATUSES =
            Set.of(ClinicStatus.PAST_DUE, ClinicStatus.BLOCKED, ClinicStatus.CANCELLED);

    private final ClinicRepository clinicRepository;

    public ClinicStatusInterceptor(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!TenantContext.isAuthenticated()) {
            return true;
        }

        Clinic clinic = clinicRepository.findById(TenantContext.currentClinicId()).orElse(null);
        if (clinic != null && BLOCKED_STATUSES.contains(clinic.getStatus())) {
            throw new ClinicBlockedException(clinic.getStatus());
        }
        return true;
    }
}
