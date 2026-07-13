package com.senamed.backend.doctor.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record DoctorUpdateRequest(
        @NotBlank(message = "name is required") String name,
        String specialty,
        @Email(message = "email must be valid") String email,
        String phone) {
}
