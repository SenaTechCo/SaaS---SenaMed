package com.senamed.backend.auth.dto;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicStatus;

import java.time.Instant;

public record AuthClinicDto(Long id, String name, String slug, ClinicStatus status, String timezone, Instant trialEndsAt) {

    public static AuthClinicDto from(Clinic clinic) {
        return new AuthClinicDto(
                clinic.getId(),
                clinic.getName(),
                clinic.getSlug(),
                clinic.getStatus(),
                clinic.getTimezone(),
                clinic.getTrialEndsAt());
    }
}
