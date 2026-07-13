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

    /**
     * Used by the public clinic page (no authenticated tenant context - see
     * {@code PublicSchedulingService}) to list only the doctors a patient is allowed to book,
     * explicitly scoped by the {@code clinicId} resolved from the clinic's slug.
     */
    List<Doctor> findAllByClinicIdAndActiveTrueOrderByNameAsc(Long clinicId);

    /**
     * Used by the public scheduling endpoints, which have no authenticated clinic to scope by
     * (see {@code PublicSchedulingService}). {@code active = true} is mandatory here so inactive
     * doctors are never exposed/bookable through the public API.
     */
    Optional<Doctor> findByIdAndActiveTrue(Long id);
}
