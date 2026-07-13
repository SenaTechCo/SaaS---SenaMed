package com.senamed.backend.clinic;

import com.senamed.backend.clinic.dto.ClinicProfileResponse;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads/updates the clinic of the currently authenticated user (RF-004, RF-005). {@code Clinic}
 * is the tenant root itself (not a tenant-scoped child entity), so it is fetched directly by the
 * id carried in the JWT via {@link TenantContext}, rather than through the
 * {@link com.senamed.backend.tenant.TenantScopedEntity} Hibernate filter mechanism.
 */
@Service
public class ClinicService {

    private final ClinicRepository clinicRepository;

    public ClinicService(ClinicRepository clinicRepository) {
        this.clinicRepository = clinicRepository;
    }

    @Transactional(readOnly = true)
    public ClinicProfileResponse getCurrentClinic() {
        return ClinicProfileResponse.from(loadCurrentClinic());
    }

    @Transactional
    public ClinicProfileResponse updateCurrentClinic(ClinicUpdateRequest request) {
        Clinic clinic = loadCurrentClinic();
        clinic.setName(request.name());
        clinic.setDescription(request.description());
        clinic.setPhone(request.phone());
        clinic.setEmail(request.email());
        clinic.setTimezone(request.timezone());
        clinic.setLogoUrl(request.logoUrl());
        clinic.setCoverImageUrl(request.coverImageUrl());
        clinic.setPrimaryColor(request.primaryColor());
        clinic.setSecondaryColor(request.secondaryColor());
        return ClinicProfileResponse.from(clinic);
    }

    private Clinic loadCurrentClinic() {
        Long clinicId = TenantContext.currentClinicId();
        return clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));
    }
}
