package com.senamed.backend.notification;

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
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * One row per email to send for an {@link Appointment} (an outbox), created by
 * {@link AppointmentMessageService} in response to {@code AppointmentCreatedEvent}/
 * {@code AppointmentCancelledEvent} and processed by {@link AppointmentMessageScheduler}.
 *
 * <p>{@link #confirmationToken} is DB-generated (mirrors {@code Appointment.cancelToken}'s idiom)
 * and is unconditionally populated for every row, even though it is only ever used for
 * {@link MessageType#REMINDER_24H} rows - {@link MessageType#CREATED_CONFIRMATION} rows simply
 * never expose it to a patient.</p>
 */
@Entity
@Table(name = "appointment_messages")
public class AppointmentMessage {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private MessageType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MessageStatus status = MessageStatus.PENDING;

    @Generated(event = EventType.INSERT)
    @Column(name = "confirmation_token", insertable = false, updatable = false, nullable = false)
    private UUID confirmationToken;

    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    @Column(name = "scheduled_for", nullable = false, updatable = false)
    private LocalDateTime scheduledFor;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    @Column(nullable = false)
    private int attempts = 0;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppointmentMessage() {
        // JPA
    }

    public AppointmentMessage(
            Appointment appointment, MessageType type, LocalDateTime scheduledFor, LocalDateTime tokenExpiresAt) {
        this.appointment = appointment;
        this.type = type;
        this.scheduledFor = scheduledFor;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public MessageType getType() {
        return type;
    }

    public MessageStatus getStatus() {
        return status;
    }

    public UUID getConfirmationToken() {
        return confirmationToken;
    }

    public LocalDateTime getTokenExpiresAt() {
        return tokenExpiresAt;
    }

    public LocalDateTime getScheduledFor() {
        return scheduledFor;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void markSent(LocalDateTime sentAt) {
        this.status = MessageStatus.SENT;
        this.sentAt = sentAt;
    }

    /** Increments the retry count, flipping to {@link MessageStatus#FAILED} past {@link #MAX_ATTEMPTS}. */
    public void recordFailedAttempt() {
        this.attempts++;
        if (this.attempts >= MAX_ATTEMPTS) {
            this.status = MessageStatus.FAILED;
        }
    }
}
