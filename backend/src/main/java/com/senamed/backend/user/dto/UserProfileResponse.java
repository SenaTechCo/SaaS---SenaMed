package com.senamed.backend.user.dto;

import com.senamed.backend.user.User;

import java.util.List;

public record UserProfileResponse(
        Long id,
        String name,
        String email,
        String role,
        Long doctorId,
        List<String> permissions) {

    public static UserProfileResponse from(User user) {
        return new UserProfileResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getDoctor() != null ? user.getDoctor().getId() : null,
                user.effectivePermissions().stream().map(Enum::name).toList());
    }
}
