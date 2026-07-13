package com.senamed.backend.clinic.dto;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicStatus;

import java.time.Instant;

public record ClinicProfileResponse(
        Long id,
        String name,
        String slug,
        String description,
        String phone,
        String email,
        String timezone,
        ClinicStatus status,
        Instant trialEndsAt) {

    public static ClinicProfileResponse from(Clinic clinic) {
        return new ClinicProfileResponse(
                clinic.getId(),
                clinic.getName(),
                clinic.getSlug(),
                clinic.getDescription(),
                clinic.getPhone(),
                clinic.getEmail(),
                clinic.getTimezone(),
                clinic.getStatus(),
                clinic.getTrialEndsAt());
    }
}
