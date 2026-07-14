package com.senamed.backend.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    boolean existsBySlug(String slug);

    Optional<Clinic> findBySlug(String slug);

    /** Trial clinics whose grace period has already ended (RF-026) - candidates for {@code TRIAL -> BLOCKED}. */
    List<Clinic> findByStatusAndTrialEndsAtBefore(ClinicStatus status, Instant trialEndsAt);
}
