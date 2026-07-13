package com.senamed.backend.doctor.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;

/**
 * {@code endDate} is optional and defaults to {@code startDate} (a single-day time off) - see
 * {@code DoctorService.createTimeOff}.
 */
public record TimeOffRequest(
        @NotNull(message = "startDate is required") LocalDate startDate,
        LocalDate endDate,
        String reason) {
}
