package com.senamed.backend.appointment.event;

/**
 * Published after a booking transaction commits - listeners (e.g. the notification package) must
 * re-fetch the {@code Appointment} by id in their own transaction rather than relying on the
 * original request's persistence context.
 */
public record AppointmentCreatedEvent(Long appointmentId) {
}
