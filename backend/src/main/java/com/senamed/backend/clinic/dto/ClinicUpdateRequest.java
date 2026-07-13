package com.senamed.backend.clinic.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Fields editable via PUT /api/clinics/me (RF-005). Slug, status and maxDoctors are intentionally
 * absent - they cannot be changed through this endpoint (maxDoctors is plan-controlled, see
 * {@link com.senamed.backend.clinic.Clinic#getMaxDoctors()}).
 *
 * <p>{@code logoUrl}/{@code coverImageUrl} are plain URL strings to externally hosted images -
 * this endpoint intentionally does not handle file upload (out of scope for Fase 2).</p>
 */
public record ClinicUpdateRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        String phone,
        @Email(message = "email must be valid") String email,
        @NotBlank(message = "timezone is required") String timezone,
        String logoUrl,
        String coverImageUrl,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "primaryColor must be a hex color like #1a73e8")
        String primaryColor,
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "secondaryColor must be a hex color like #1a73e8")
        String secondaryColor) {
}
