package com.senamed.backend.user.dto;

import com.senamed.backend.user.User;

import java.util.List;

public record UserManagementResponse(
        Long id,
        String name,
        String email,
        String role,
        List<String> permissions,
        Long doctorId,
        String doctorName,
        String doctorSpecialty) {

    public static UserManagementResponse from(User user) {
        Long doctorId = user.getDoctor() != null ? user.getDoctor().getId() : null;
        String doctorName = user.getDoctor() != null ? user.getDoctor().getName() : null;
        String doctorSpecialty = user.getDoctor() != null ? user.getDoctor().getSpecialty() : null;
        return new UserManagementResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole().name(),
                user.getPermissions().stream().map(Enum::name).toList(),
                doctorId,
                doctorName,
                doctorSpecialty);
    }
}
