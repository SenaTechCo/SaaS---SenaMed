package com.senamed.backend.auth.dto;

import com.senamed.backend.user.User;
import com.senamed.backend.user.UserRole;

public record AuthUserDto(Long id, String name, String email, UserRole role) {

    public static AuthUserDto from(User user) {
        return new AuthUserDto(user.getId(), user.getName(), user.getEmail(), user.getRole());
    }
}
