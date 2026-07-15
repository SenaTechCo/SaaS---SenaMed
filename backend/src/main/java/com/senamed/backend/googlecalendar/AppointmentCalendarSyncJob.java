package com.senamed.backend.googlecalendar;

import com.senamed.backend.appointment.Appointment;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * One row per Google Calendar event operation to perform for an {@link Appointment} (an outbox,
 * mirroring {@code AppointmentMessage} from Fase 4) - created by
 * {@code AppointmentCalendarSyncService} in response to {@code AppointmentCreatedEvent}/
 * {@code AppointmentCancelledEvent} and processed by {@code AppointmentCalendarSyncScheduler}.
 */
@Entity
@Table(name = "appointment_calendar_sync_jobs")
public class AppointmentCalendarSyncJob {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private SyncJobType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SyncJobStatus status = SyncJobStatus.PENDING;

    @Column(name = "google_event_id")
    private String googleEventId;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppointmentCalendarSyncJob() {
        // JPA
    }

    public AppointmentCalendarSyncJob(Appointment appointment, SyncJobType type) {
        this.appointment = appointment;
        this.type = type;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public SyncJobType getType() {
        return type;
    }

    public SyncJobStatus getStatus() {
        return status;
    }

    public String getGoogleEventId() {
        return googleEventId;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** {@code googleEventId} is only meaningful for {@link SyncJobType#CREATE_EVENT} jobs. */
    public void markSent(String googleEventId) {
        this.status = SyncJobStatus.SENT;
        this.googleEventId = googleEventId;
    }

    /** Increments the retry count, flipping to {@link SyncJobStatus#FAILED} past {@link #MAX_ATTEMPTS}. */
    public void recordFailedAttempt() {
        this.attempts++;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = SyncJobStatus.FAILED;
        }
    }

    /** The appointment was cancelled before this (still-PENDING) job ran - never create the event. */
    public void markSkipped() {
        this.status = SyncJobStatus.SKIPPED;
    }
}
