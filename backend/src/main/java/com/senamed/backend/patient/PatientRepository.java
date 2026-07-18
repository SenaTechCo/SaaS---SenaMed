package com.senamed.backend.patient;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PatientRepository extends JpaRepository<Patient, Long> {

    List<Patient> findAllByClinicIdOrderByNameAsc(Long clinicId);

    List<Patient> findAllByClinicIdAndNameContainingIgnoreCaseOrderByNameAsc(Long clinicId, String name);

    /**
     * Explicit tenant-scoped lookup (defense in depth) - mirrors
     * {@code DoctorRepository.findByIdAndClinicId}. Used by every patient-scoped operation
     * instead of the plain {@code findById}.
     */
    Optional<Patient> findByIdAndClinicId(Long id, Long clinicId);
}
