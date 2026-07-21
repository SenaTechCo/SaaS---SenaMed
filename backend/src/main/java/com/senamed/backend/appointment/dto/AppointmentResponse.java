package com.senamed.backend.appointment.dto;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record AppointmentResponse(
        Long id,
        Long doctorId,
        String doctorName,
        String clinicName,
        LocalDate date,
        String startTime,
        String endTime,
        Long patientId,
        String patientName,
        AppointmentStatus status,
        String cancelToken,
        Instant confirmedAt,
        List<ServiceLineResponse> services,
        BigDecimal price) {

    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(
                appointment.getId(),
                appointment.getDoctor().getId(),
                appointment.getDoctor().getName(),
                appointment.getClinic().getName(),
                appointment.getStartsAt().toLocalDate(),
                TimeFormats.HH_MM.format(appointment.getStartsAt().toLocalTime()),
                TimeFormats.HH_MM.format(appointment.getEndsAt().toLocalTime()),
                appointment.getPatient() != null ? appointment.getPatient().getId() : null,
                appointment.getPatientName(),
                appointment.getStatus(),
                appointment.getCancelToken() != null ? appointment.getCancelToken().toString() : null,
                appointment.getConfirmedAt(),
                appointment.getLineItems().stream().map(ServiceLineResponse::from).toList(),
                appointment.getPrice());
    }
}
