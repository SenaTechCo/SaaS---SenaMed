package com.senamed.backend.doctor;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DoctorTimeOffRepository extends JpaRepository<DoctorTimeOff, Long> {

    List<DoctorTimeOff> findByDoctorIdOrderByStartDateAsc(Long doctorId);
}
