package com.senamed.backend.appointment.dto;

import com.senamed.backend.doctor.Doctor;

public record PublicDoctorSummary(Long id, String name, String specialty) {

    public static PublicDoctorSummary from(Doctor doctor) {
        return new PublicDoctorSummary(doctor.getId(), doctor.getName(), doctor.getSpecialty());
    }
}
