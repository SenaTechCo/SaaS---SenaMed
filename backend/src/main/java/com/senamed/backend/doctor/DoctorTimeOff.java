package com.senamed.backend.doctor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.time.LocalDate;

/**
 * A single time-off period for a doctor. New records always accumulate (they are never replaced
 * like {@link DoctorAvailability} windows are) - a doctor can have any number of time-off periods
 * over time. Not tenant-scoped directly, same rationale as {@link DoctorAvailability}.
 */
@Entity
@Table(name = "doctor_time_off")
public class DoctorTimeOff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false)
    private Doctor doctor;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected DoctorTimeOff() {
        // JPA
    }

    public DoctorTimeOff(Doctor doctor, LocalDate startDate, LocalDate endDate, String reason) {
        this.doctor = doctor;
        this.startDate = startDate;
        this.endDate = endDate;
        this.reason = reason;
    }

    public Long getId() {
        return id;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getReason() {
        return reason;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
