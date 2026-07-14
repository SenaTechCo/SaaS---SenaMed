package com.senamed.backend.appointment.event;

/**
 * Published after a cancellation transaction commits - see {@link AppointmentCreatedEvent}'s
 * javadoc for why listeners re-fetch the entity themselves.
 */
public record AppointmentCancelledEvent(Long appointmentId) {
}
