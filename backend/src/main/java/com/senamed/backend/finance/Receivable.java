package com.senamed.backend.finance;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.tenant.TenantScopedEntity;
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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * A "Conta a Receber" (Financeiro) - auto-created when a booked appointment (with a linked
 * {@code ServiceOffering}) is marked as attended, see
 * {@code AppointmentAttendedListener}. Mirrors {@link com.senamed.backend.patient.Patient}'s
 * tenant-scoping pattern exactly - see {@link TenantScopedEntity}'s javadoc for the Hibernate
 * {@code @Filter} mechanism this class inherits.
 */
@Entity
@Table(name = "receivables")
public class Receivable extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false)
    private Doctor doctor;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReceivableStatus status = ReceivableStatus.PENDING;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Receivable() {
        // JPA
    }

    public Receivable(Clinic clinic, Appointment appointment, Doctor doctor, String description, BigDecimal amount) {
        this.clinic = clinic;
        this.appointment = appointment;
        this.doctor = doctor;
        this.description = description;
        this.amount = amount;
    }

    public Long getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public String getDescription() {
        return description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public ReceivableStatus getStatus() {
        return status;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /** Marks this receivable as paid, recording the moment it happened. */
    public void markPaid() {
        this.status = ReceivableStatus.PAID;
        this.paidAt = Instant.now();
    }
}
