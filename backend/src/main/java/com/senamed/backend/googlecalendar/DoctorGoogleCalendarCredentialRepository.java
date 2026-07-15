package com.senamed.backend.googlecalendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface DoctorGoogleCalendarCredentialRepository extends JpaRepository<DoctorGoogleCalendarCredential, Long> {

    Optional<DoctorGoogleCalendarCredential> findByDoctorId(Long doctorId);

    boolean existsByDoctorId(Long doctorId);

    void deleteByDoctorId(Long doctorId);
}
