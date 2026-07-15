package com.senamed.backend.appointment.dto;

import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;

public record AppointmentRescheduleRequest(
        @NotNull(message = "date is required") LocalDate date,
        @NotNull(message = "startTime is required") LocalTime startTime) {
}
