package com.senamed.backend.doctor.dto;

import com.senamed.backend.user.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Set;

/**
 * {@code permissions} lets a doctor be granted extra permissions beyond self-service right when
 * access is created. Left {@code null} when omitted from the request JSON - treated the same as
 * an empty set (mirrors {@code UserCreateRequest}).
 */
public record GrantDoctorAccessRequest(
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        @NotBlank(message = "password is required") @Size(min = 8, message = "password must have at least 8 characters") String password,
        Set<Permission> permissions) {
}
