package com.senamed.backend.user.dto;

import com.senamed.backend.user.Permission;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

/**
 * Edits name/email/permissions of an existing (non-ADMIN) user in the current clinic.
 *
 * <p>{@code permissions} is left {@code null} when omitted from the request JSON - treated the
 * same as an empty set, mirroring {@link UserCreateRequest}.</p>
 */
public record UserUpdateRequest(
        @NotBlank(message = "name is required") String name,
        @NotBlank(message = "email is required") @Email(message = "email must be valid") String email,
        Set<Permission> permissions) {
}
