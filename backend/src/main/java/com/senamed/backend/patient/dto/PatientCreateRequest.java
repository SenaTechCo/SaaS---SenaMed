package com.senamed.backend.patient.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.time.LocalDate;

public record PatientCreateRequest(
        @NotBlank(message = "name is required") String name,
        String socialName,
        LocalDate birthDate,
        String sex,
        String cpf,
        @Email(message = "email must be valid") String email,
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
        boolean lgpdConsent) {
}
