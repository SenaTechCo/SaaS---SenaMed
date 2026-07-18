package com.senamed.backend.patient.dto;

import com.senamed.backend.patient.Patient;

import java.time.Instant;
import java.time.LocalDate;

public record PatientResponse(
        Long id,
        String name,
        String socialName,
        LocalDate birthDate,
        String sex,
        String cpf,
        String email,
        String phone,
        String zipCode,
        String street,
        String number,
        String complement,
        String neighborhood,
        String city,
        String state,
        String referralSource,
        String notes,
        boolean lgpdConsent,
        Instant lgpdConsentAt,
        boolean active,
        Instant createdAt) {

    public static PatientResponse from(Patient patient) {
        return new PatientResponse(
                patient.getId(),
                patient.getName(),
                patient.getSocialName(),
                patient.getBirthDate(),
                patient.getSex(),
                patient.getCpf(),
                patient.getEmail(),
                patient.getPhone(),
                patient.getZipCode(),
                patient.getStreet(),
                patient.getNumber(),
                patient.getComplement(),
                patient.getNeighborhood(),
                patient.getCity(),
                patient.getState(),
                patient.getReferralSource(),
                patient.getNotes(),
                patient.isLgpdConsent(),
                patient.getLgpdConsentAt(),
                patient.isActive(),
                patient.getCreatedAt());
    }
}
