package com.senamed.backend.googlecalendar;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.event.AppointmentCancelledEvent;
import com.senamed.backend.appointment.event.AppointmentCreatedEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Owns the {@code appointment_calendar_sync_jobs} outbox (KAN-78) - creates rows in response to
 * booking/cancellation events, mirroring {@code AppointmentMessageService}'s handling of the
 * {@code appointment_messages} outbox for the exact same events (Fase 4). Never calls the Google
 * Calendar API directly - that only ever happens in {@link AppointmentCalendarSyncProcessor}.
 */
@Service
public class AppointmentCalendarSyncService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentCalendarSyncJobRepository syncJobRepository;
    private final DoctorGoogleCalendarCredentialRepository credentialRepository;

    public AppointmentCalendarSyncService(
            AppointmentRepository appointmentRepository,
            AppointmentCalendarSyncJobRepository syncJobRepository,
            DoctorGoogleCalendarCredentialRepository credentialRepository) {
        this.appointmentRepository = appointmentRepository;
        this.syncJobRepository = syncJobRepository;
        this.credentialRepository = credentialRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                .orElseThrow(() -> new IllegalStateException("Appointment not found: " + event.appointmentId()));

        // Most appointments won't have a connected doctor - skip creating a useless outbox row.
        if (!credentialRepository.existsByDoctorId(appointment.getDoctor().getId())) {
            return;
        }
        syncJobRepository.save(new AppointmentCalendarSyncJob(appointment, SyncJobType.CREATE_EVENT));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                .orElseThrow(() -> new IllegalStateException("Appointment not found: " + event.appointmentId()));

        // Race guard: if the CREATE_EVENT job hasn't run yet, skip it entirely rather than
        // creating a Google event only to immediately delete it.
        syncJobRepository.skipPendingCreateJobsByAppointmentId(appointment.getId());

        // If a CREATE_EVENT job already ran (a real Google event exists), schedule its deletion.
        boolean hadSentCreateJob = syncJobRepository.existsByAppointmentIdAndTypeAndStatus(
                appointment.getId(), SyncJobType.CREATE_EVENT, SyncJobStatus.SENT);
        if (hadSentCreateJob) {
            syncJobRepository.save(new AppointmentCalendarSyncJob(appointment, SyncJobType.CANCEL_EVENT));
        }
    }
}
