package com.senamed.backend.auth.dto;

import com.senamed.backend.user.User;
import com.senamed.backend.user.UserRole;

import java.util.List;

public record AuthUserDto(Long id, String name, String email, UserRole role, Long doctorId, List<String> permissions) {

    public static AuthUserDto from(User user) {
        Long doctorId = user.getDoctor() != null ? user.getDoctor().getId() : null;
        List<String> permissions = user.effectivePermissions().stream().map(Enum::name).toList();
        return new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), doctorId, permissions);
    }
}
