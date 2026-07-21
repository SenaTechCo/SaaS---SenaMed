package com.senamed.backend.finance;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.event.AppointmentAttendedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owns the auto-billing side effect of marking an appointment as attended (KAN-100): creates a
 * {@code Receivable} in response to the event published by {@code AppointmentService.markAttended},
 * mirroring {@code AppointmentMessageService}/{@code AppointmentCalendarSyncService}'s handling of
 * the appointment lifecycle events (re-fetches the entity by id in its own transaction rather than
 * trusting the original request's persistence context - see {@code AppointmentCreatedEvent}'s
 * javadoc).
 */
@Component
public class AppointmentAttendedListener {

    private final ReceivableRepository receivableRepository;
    private final AppointmentRepository appointmentRepository;

    public AppointmentAttendedListener(ReceivableRepository receivableRepository, AppointmentRepository appointmentRepository) {
        this.receivableRepository = receivableRepository;
        this.appointmentRepository = appointmentRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentAttended(AppointmentAttendedEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                .orElseThrow(() -> new IllegalStateException("Appointment not found: " + event.appointmentId()));

        // No service was selected at booking time - nothing to bill.
        if (appointment.getPrice() == null) {
            return;
        }

        receivableRepository.save(new Receivable(
                appointment.getClinic(), appointment, appointment.getDoctor(),
                appointment.getService().getName(), appointment.getPrice()));
    }
}
