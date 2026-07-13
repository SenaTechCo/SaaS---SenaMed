package com.senamed.backend.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DoctorRepository extends JpaRepository<Doctor, Long> {

    List<Doctor> findAllByClinicIdOrderByNameAsc(Long clinicId);

    /**
     * Explicit tenant-scoped lookup (defense in depth) - see {@link Doctor}'s javadoc. Used by
     * every doctor-scoped operation instead of the plain {@code findById}.
     */
    Optional<Doctor> findByIdAndClinicId(Long id, Long clinicId);

    long countByClinicIdAndActiveTrue(Long clinicId);
}
