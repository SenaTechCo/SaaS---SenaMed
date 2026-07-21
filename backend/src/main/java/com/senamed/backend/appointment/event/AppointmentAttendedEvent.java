package com.senamed.backend.appointment.event;

/**
 * Published after a staff member marks an appointment as attended (KAN-100) commits - see
 * {@link AppointmentCreatedEvent}'s javadoc for why listeners re-fetch the entity themselves.
 */
public record AppointmentAttendedEvent(Long appointmentId) {
}
