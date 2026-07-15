package com.senamed.backend.auth.dto;

import com.senamed.backend.user.User;
import com.senamed.backend.user.UserRole;

public record AuthUserDto(Long id, String name, String email, UserRole role, Long doctorId) {

    public static AuthUserDto from(User user) {
        Long doctorId = user.getDoctor() != null ? user.getDoctor().getId() : null;
        return new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getRole(), doctorId);
    }
}
