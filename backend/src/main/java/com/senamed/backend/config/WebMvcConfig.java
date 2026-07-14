package com.senamed.backend.config;

import com.senamed.backend.tenant.ClinicStatusInterceptor;
import com.senamed.backend.tenant.TenantFilterInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final TenantFilterInterceptor tenantFilterInterceptor;
    private final ClinicStatusInterceptor clinicStatusInterceptor;

    public WebMvcConfig(TenantFilterInterceptor tenantFilterInterceptor, ClinicStatusInterceptor clinicStatusInterceptor) {
        this.tenantFilterInterceptor = tenantFilterInterceptor;
        this.clinicStatusInterceptor = clinicStatusInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Runs before TenantFilterInterceptor: a blocked clinic is rejected before we even bother
        // enabling the Hibernate tenant filter. Billing endpoints stay reachable so a blocked
        // clinic can still pay to unblock itself (RF-022/RN-007).
        registry.addInterceptor(clinicStatusInterceptor)
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/clinics/me", "/api/plans", "/api/plans/**", "/api/subscriptions/**");
        registry.addInterceptor(tenantFilterInterceptor).addPathPatterns("/api/**");
    }
}
