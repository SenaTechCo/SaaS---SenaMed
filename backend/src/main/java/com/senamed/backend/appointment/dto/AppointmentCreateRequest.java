package com.senamed.backend.appointment.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/**
 * {@code lgpdConsent} is deliberately not {@code @NotNull}/{@code @AssertTrue} here - a missing or
 * false value is a business rule (RF-025), not a shape/type problem, so it is validated in
 * {@code PublicSchedulingService} to produce a clear, specific message instead of a generic
 * "validation failed" one.
 *
 * <p>{@code services} is left {@code null} when omitted from the request JSON (Jackson does not
 * default a missing array field to empty) - callers must treat {@code null} the same as an empty
 * list (KAN-103).</p>
 */
public record AppointmentCreateRequest(
        @NotNull(message = "doctorId is required") Long doctorId,
        Long patientId,
        @Valid List<ServiceItemRequest> services,
        @NotNull(message = "date is required") LocalDate date,
        @NotNull(message = "startTime is required") LocalTime startTime,
        @NotBlank(message = "patientName is required") String patientName,
        @NotBlank(message = "patientEmail is required") @Email(message = "patientEmail must be valid") String patientEmail,
        String patientPhone,
        Boolean lgpdConsent) {
}
