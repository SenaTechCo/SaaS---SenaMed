package com.senamed.backend.auth.dto;

public record AuthResponse(String token, AuthClinicDto clinic, AuthUserDto user) {
}
