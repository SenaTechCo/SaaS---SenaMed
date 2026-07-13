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
}
