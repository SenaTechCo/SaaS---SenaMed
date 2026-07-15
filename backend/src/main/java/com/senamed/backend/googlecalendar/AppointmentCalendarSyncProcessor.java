package com.senamed.backend.googlecalendar;

import com.senamed.backend.appointment.Appointment;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Processes (or retries) a single {@link AppointmentCalendarSyncJob} - the only place that ever
 * calls {@link GoogleCalendarClient}. Kept as a separate bean from
 * {@link AppointmentCalendarSyncScheduler} so {@link #processOne}'s {@code @Transactional} goes
 * through Spring's proxy, mirroring {@code AppointmentMessageProcessor}'s exact rationale.
 */
@Component
public class AppointmentCalendarSyncProcessor {

    private static final Logger log = LoggerFactory.getLogger(AppointmentCalendarSyncProcessor.class);

    private final AppointmentCalendarSyncJobRepository syncJobRepository;
    private final DoctorGoogleCalendarCredentialRepository credentialRepository;
    private final GoogleCalendarClient googleCalendarClient;

    public AppointmentCalendarSyncProcessor(
            AppointmentCalendarSyncJobRepository syncJobRepository,
            DoctorGoogleCalendarCredentialRepository credentialRepository,
            GoogleCalendarClient googleCalendarClient) {
        this.syncJobRepository = syncJobRepository;
        this.credentialRepository = credentialRepository;
        this.googleCalendarClient = googleCalendarClient;
    }

    @Transactional
    public void processOne(Long jobId) {
        AppointmentCalendarSyncJob job = syncJobRepository.findById(jobId).orElse(null);
        if (job == null || job.getStatus() != SyncJobStatus.PENDING) {
            return; // race guard: already handled by a concurrent tick
        }

        Appointment appointment = job.getAppointment();
        DoctorGoogleCalendarCredential credential =
                credentialRepository.findByDoctorId(appointment.getDoctor().getId()).orElse(null);
        if (credential == null) {
            job.markSkipped(); // doctor disconnected between the event firing and this job running
            return;
        }

        try {
            if (job.getType() == SyncJobType.CREATE_EVENT) {
                String googleEventId = googleCalendarClient.createEvent(
                        credential.getRefreshToken(), CreateCalendarEventCommand.from(appointment));
                job.markSent(googleEventId);
            } else {
                syncJobRepository
                        .findFirstByAppointmentIdAndTypeAndStatusOrderByCreatedAtDesc(
                                appointment.getId(), SyncJobType.CREATE_EVENT, SyncJobStatus.SENT)
                        .map(AppointmentCalendarSyncJob::getGoogleEventId)
                        .ifPresent(googleEventId -> googleCalendarClient.deleteEvent(credential.getRefreshToken(), googleEventId));
                job.markSent(null);
            }
        } catch (Exception ex) {
            log.warn("Failed to sync calendar job {} (attempt {}): {}", jobId, job.getAttempts() + 1, ex.getMessage());
            job.recordFailedAttempt();
        }
    }
}
