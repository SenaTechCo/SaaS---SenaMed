package com.senamed.backend.appointment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Line items are always accessed through their parent {@code Appointment} ({@code
 * appointment.getLineItems()}) - this interface exists so Spring Data manages the entity (and
 * cascades from {@link Appointment} work correctly), not because any custom query is needed here.
 */
public interface AppointmentLineItemRepository extends JpaRepository<AppointmentLineItem, Long> {
}
