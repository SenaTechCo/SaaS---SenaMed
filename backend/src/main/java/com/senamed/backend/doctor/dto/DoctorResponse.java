package com.senamed.backend.doctor.dto;

import com.senamed.backend.doctor.Doctor;

import java.time.Instant;

public record DoctorResponse(
        Long id,
        String name,
        String specialty,
        String email,
        String phone,
        boolean active,
        Instant createdAt,
        boolean hasLoginAccess) {

    public static DoctorResponse from(Doctor doctor, boolean hasLoginAccess) {
        return new DoctorResponse(
                doctor.getId(),
                doctor.getName(),
                doctor.getSpecialty(),
                doctor.getEmail(),
                doctor.getPhone(),
                doctor.isActive(),
                doctor.getCreatedAt(),
                hasLoginAccess);
    }
}
