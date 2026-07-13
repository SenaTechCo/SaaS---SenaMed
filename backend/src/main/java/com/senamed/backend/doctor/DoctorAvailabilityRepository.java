package com.senamed.backend.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorAvailabilityRepository extends JpaRepository<DoctorAvailability, Long> {

    List<DoctorAvailability> findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(Long doctorId);

    /** Used by POST /api/doctors/{id}/availability to replace all windows (delete + insert). */
    void deleteByDoctorId(Long doctorId);
}
