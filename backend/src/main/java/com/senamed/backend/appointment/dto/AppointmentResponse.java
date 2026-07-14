package com.senamed.backend.appointment.dto;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentStatus;

import java.time.Instant;
import java.time.LocalDate;

public record AppointmentResponse(
        Long id,
        Long doctorId,
        String doctorName,
        String clinicName,
        LocalDate date,
        String startTime,
        String endTime,
        String patientName,
        AppointmentStatus status,
        String cancelToken,
        Instant confirmedAt) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getName(),
                appointment.getClinic().getName(),
                appointment.getStartsAt().toLocalDate(),
                TimeFormats.HH_MM.format(appointment.getStartsAt().toLocalTime()),
                TimeFormats.HH_MM.format(appointment.getEndsAt().toLocalTime()),
                appointment.getPatientName(),
                appointment.getStatus(),
                appointment.getCancelToken() != null ? appointment.getCancelToken().toString() : null,
                appointment.getConfirmedAt());
    }
}
