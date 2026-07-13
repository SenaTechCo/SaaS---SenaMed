package com.senamed.backend.doctor.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.time.LocalTime;

/**
 * One weekly availability window. {@code dayOfWeek}: 1 = Monday ... 7 = Sunday (ISO-8601).
 * {@code startTime}/{@code endTime} accept "HH:mm" (seconds optional) and must satisfy
 * {@code startTime < endTime} - enforced in {@code DoctorService}, not expressible with plain
 * Bean Validation annotations since it is a cross-field rule.
 */
public record AvailabilityRequest(
        @NotNull(message = "dayOfWeek is required")
        @Min(value = 1, message = "dayOfWeek must be between 1 and 7")
        @Max(value = 7, message = "dayOfWeek must be between 1 and 7")
        Integer dayOfWeek,

        @NotNull(message = "startTime is required") LocalTime startTime,

        @NotNull(message = "endTime is required") LocalTime endTime) {
}
