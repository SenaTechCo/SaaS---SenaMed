package com.senamed.backend.user.dto;

import com.senamed.backend.user.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * Creates a new {@code STAFF} user in the current clinic.
 *
 * <p>{@code permissions} is left {@code null} when omitted from the request JSON (Jackson does
 * not default a missing field to empty) - callers must treat {@code null} the same as an empty
 * set, mirroring {@code AppointmentCreateRequest.services} (KAN-103): the admin must explicitly
 * opt a new staff member into each permission.</p>
 */
public record UserCreateRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        @NotBlank(message = "password is required") @Size(min = 8, message = "password must have at least 8 characters") String password,
        Set<Permission> permissions) {
}
