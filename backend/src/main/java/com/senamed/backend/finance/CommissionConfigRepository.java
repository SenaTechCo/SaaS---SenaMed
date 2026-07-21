package com.senamed.backend.finance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CommissionConfigRepository extends JpaRepository<CommissionConfig, Long> {

    Optional<CommissionConfig> findByClinicIdAndDoctorId(Long clinicId, Long doctorId);
}
