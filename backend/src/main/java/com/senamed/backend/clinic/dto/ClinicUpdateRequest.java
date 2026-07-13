package com.senamed.backend.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Fields editable via PUT /api/clinics/me (RF-005). Slug and status are intentionally absent -
 * they cannot be changed through this endpoint.
 */
public record ClinicUpdateRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        String phone,
        @Email(message = "email must be valid") String email,
        @NotBlank(message = "timezone is required") String timezone) {
}
