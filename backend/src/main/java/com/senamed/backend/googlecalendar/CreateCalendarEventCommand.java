package com.senamed.backend.googlecalendar;

import com.senamed.backend.appointment.Appointment;

import java.time.LocalDateTime;

public record CreateCalendarEventCommand(
        String summary,
        String description,
        LocalDateTime startsAt,
        LocalDateTime endsAt,
        String timezone) {

    public static CreateCalendarEventCommand from(Appointment appointment) {
        return new CreateCalendarEventCommand(
                "Consulta: " + appointment.getPatientName(),
                "Agendamento SenaMed - " + appointment.getClinic().getName(),
                appointment.getStartsAt(),
                appointment.getEndsAt(),
                appointment.getClinic().getTimezone());
    }
}
