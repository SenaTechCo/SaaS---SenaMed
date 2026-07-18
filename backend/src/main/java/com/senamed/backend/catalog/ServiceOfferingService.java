package com.senamed.backend.catalog;

import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.catalog.dto.ServiceOfferingUpdateRequest;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Service/procedure catalog CRUD (Catalogo de Servicos). Mirrors {@code PatientService}'s
 * tenant-scoping pattern exactly - every lookup here is explicitly scoped by
 * {@link TenantContext#currentClinicId()} in addition to the Hibernate filter, so a clinic can
 * never see or mutate another clinic's service offerings.
 */
@Service
public class ServiceOfferingService {

    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ClinicRepository clinicRepository;

    public ServiceOfferingService(ServiceOfferingRepository serviceOfferingRepository, ClinicRepository clinicRepository) {
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional
    public ServiceOfferingResponse create(ServiceOfferingCreateRequest request) {
        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));

        ServiceOffering serviceOffering = new ServiceOffering(clinic, request.name(), request.durationMinutes(), request.price());
        serviceOffering.setDescription(request.description());

        serviceOffering = serviceOfferingRepository.save(serviceOffering);
        return ServiceOfferingResponse.from(serviceOffering);
    }

    @Transactional(readOnly = true)
    public List<ServiceOfferingResponse> listAll(String search) {
        Long clinicId = TenantContext.currentClinicId();
        List<ServiceOffering> serviceOfferings = StringUtils.hasText(search)
                ? serviceOfferingRepository.findAllByClinicIdAndNameContainingIgnoreCaseOrderByNameAsc(clinicId, search.trim())
                : serviceOfferingRepository.findAllByClinicIdOrderByNameAsc(clinicId);
        return serviceOfferings.stream().map(ServiceOfferingResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public ServiceOfferingResponse getOne(Long id) {
        return ServiceOfferingResponse.from(loadOwnedServiceOffering(id));
    }

    @Transactional
    public ServiceOfferingResponse update(Long id, ServiceOfferingUpdateRequest request) {
        ServiceOffering serviceOffering = loadOwnedServiceOffering(id);
        serviceOffering.setName(request.name());
        serviceOffering.setDescription(request.description());
        serviceOffering.setDurationMinutes(request.durationMinutes());
        serviceOffering.setPrice(request.price());
        return ServiceOfferingResponse.from(serviceOffering);
    }

    /** Soft delete: sets {@code active = false}, the row is never removed. */
    @Transactional
    public void deactivate(Long id) {
        loadOwnedServiceOffering(id).deactivate();
    }

    @Transactional
    public ServiceOfferingResponse restore(Long id) {
        ServiceOffering serviceOffering = loadOwnedServiceOffering(id);
        serviceOffering.restore();
        return ServiceOfferingResponse.from(serviceOffering);
    }

    /**
     * Loads a service offering, explicitly scoped to the current clinic. Returns 404 (via
     * {@link ResourceNotFoundException}) both when the id does not exist and when it belongs to
     * another clinic, so callers cannot distinguish "not found" from "not yours".
     */
    private ServiceOffering loadOwnedServiceOffering(Long id) {
        return serviceOfferingRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Service not found: " + id));
    }
}
