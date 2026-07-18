package com.senamed.backend.catalog;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceOfferingRepository extends JpaRepository<ServiceOffering, Long> {

    List<ServiceOffering> findAllByClinicIdOrderByNameAsc(Long clinicId);

    List<ServiceOffering> findAllByClinicIdAndNameContainingIgnoreCaseOrderByNameAsc(Long clinicId, String name);

    /**
     * Explicit tenant-scoped lookup (defense in depth) - mirrors
     * {@code PatientRepository.findByIdAndClinicId}. Used by every service-offering-scoped
     * operation instead of the plain {@code findById}.
     */
    Optional<ServiceOffering> findByIdAndClinicId(Long id, Long clinicId);
}
