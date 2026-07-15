package com.senamed.backend.googlecalendar;

import com.senamed.backend.doctor.Doctor;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A doctor's connected Google Calendar (KAN-78) - at most one per doctor (DB unique constraint
 * on {@code doctor_id}). {@link #refreshToken} is encrypted at rest via
 * {@link EncryptedStringConverter}, transparently to every caller in this class.
 */
@Entity
@Table(name = "doctor_google_calendar_credentials")
public class DoctorGoogleCalendarCredential {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false)
    private Doctor doctor;

    @Column(name = "google_email", nullable = false)
    private String googleEmail;

    @Convert(converter = EncryptedStringConverter.class)
    @Column(name = "refresh_token", nullable = false)
    private String refreshToken;

    @Column(name = "connected_at", nullable = false, updatable = false)
    private Instant connectedAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected DoctorGoogleCalendarCredential() {
        // JPA
    }

    public DoctorGoogleCalendarCredential(Doctor doctor, String googleEmail, String refreshToken) {
        this.doctor = doctor;
        this.googleEmail = googleEmail;
        this.refreshToken = refreshToken;
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public String getGoogleEmail() {
        return googleEmail;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Reconnecting (e.g. after revoking access on Google's side) updates in place instead of duplicating. */
    public void reconnect(String googleEmail, String refreshToken) {
        this.googleEmail = googleEmail;
        this.refreshToken = refreshToken;
        this.updatedAt = Instant.now();
    }
}
