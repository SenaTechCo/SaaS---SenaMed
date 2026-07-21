package com.senamed.backend.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentRepository extends JpaRepository<Appointment, Long> {

    Optional<Appointment> findByCancelToken(UUID cancelToken);

    /** Used to compute occupied slots for a given day (RF-013): all CONFIRMED appointments of a
     *  doctor whose start falls within [dayStart, dayEnd). */
    List<Appointment> findByDoctorIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
            Long doctorId, AppointmentStatus status, LocalDateTime dayStart, LocalDateTime dayEnd);

    /** Backs the authenticated "dashboard de consultas" listing (RF-018). */
    List<Appointment> findAllByClinicIdOrderByStartsAtAsc(Long clinicId);

    /** Backs the doctor's own-agenda view (KAN-77): a doctor's own appointments within their clinic. */
    List<Appointment> findAllByClinicIdAndDoctorIdOrderByStartsAtAsc(Long clinicId, Long doctorId);

    /** Backs the dashboard reports (KAN-102): all of a clinic's appointments starting within a date range. */
    List<Appointment> findAllByClinicIdAndStartsAtBetween(Long clinicId, LocalDateTime start, LocalDateTime end);
}
