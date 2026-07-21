package com.senamed.backend.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ReceivableRepository extends JpaRepository<Receivable, Long> {

    List<Receivable> findAllByClinicIdOrderByCreatedAtDesc(Long clinicId);

    List<Receivable> findAllByClinicIdAndStatusOrderByCreatedAtDesc(Long clinicId, ReceivableStatus status);

    /**
     * Explicit tenant-scoped lookup (defense in depth) - mirrors
     * {@code PatientRepository.findByIdAndClinicId}. Used by every receivable-scoped operation
     * instead of the plain {@code findById}.
     */
    Optional<Receivable> findByIdAndClinicId(Long id, Long clinicId);

    /** Used to compute a doctor's commission for a given month (the range is the caller's responsibility). */
    List<Receivable> findAllByClinicIdAndDoctorIdAndCreatedAtBetween(
            Long clinicId, Long doctorId, Instant start, Instant end);

    /** Backs the dashboard reports (KAN-102): all of a clinic's receivables created within a date range. */
    List<Receivable> findAllByClinicIdAndCreatedAtBetween(Long clinicId, Instant start, Instant end);
}
