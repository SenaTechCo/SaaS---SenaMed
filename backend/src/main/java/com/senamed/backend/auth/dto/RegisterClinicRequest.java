package com.senamed.backend.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterClinicRequest(
        @NotBlank(message = "clinicName is required") String clinicName,
        @NotBlank(message = "adminName is required") String adminName,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        @NotBlank(message = "password is required") @Size(min = 8, message = "password must have at least 8 characters") String password) {
}
