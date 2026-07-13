package com.senamed.backend.appointment.dto;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.doctor.Doctor;

import java.util.List;

/** GET /api/public/clinics/{slug} - only exposes what a patient needs to book (RF-011). */
public record PublicClinicResponse(
        String slug,
        String name,
        String description,
        String phone,
        String email,
        String timezone,
        String logoUrl,
        String coverImageUrl,
        String primaryColor,
        String secondaryColor,
        List<PublicDoctorSummary> doctors) {

    public static PublicClinicResponse from(Clinic clinic, List<Doctor> activeDoctors) {
        return new PublicClinicResponse(
                clinic.getSlug(),
                clinic.getName(),
                clinic.getDescription(),
                clinic.getPhone(),
                clinic.getEmail(),
                clinic.getTimezone(),
                clinic.getLogoUrl(),
                clinic.getCoverImageUrl(),
                clinic.getPrimaryColor(),
                clinic.getSecondaryColor(),
                activeDoctors.stream().map(PublicDoctorSummary::from).toList());
    }
}
