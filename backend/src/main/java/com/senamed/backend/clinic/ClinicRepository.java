package com.senamed.backend.clinic;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {

    boolean existsBySlug(String slug);

    Optional<Clinic> findBySlug(String slug);
}
