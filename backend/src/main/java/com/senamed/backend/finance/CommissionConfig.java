package com.senamed.backend.finance;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.tenant.TenantScopedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;

/**
 * A doctor's commission percentage on billed services (Financeiro). Mirrors
 * {@link com.senamed.backend.patient.Patient}'s tenant-scoping pattern exactly - see
 * {@link TenantScopedEntity}'s javadoc for the Hibernate {@code @Filter} mechanism this class
 * inherits.
 */
@Entity
@Table(name = "commission_configs")
public class CommissionConfig extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = false, updatable = false)
    private Doctor doctor;

    @Column(nullable = false)
    private BigDecimal percentage;

    @Column(nullable = false)
    private boolean active = true;

    protected CommissionConfig() {
        // JPA
    }

    public CommissionConfig(Clinic clinic, Doctor doctor, BigDecimal percentage) {
        this.clinic = clinic;
        this.doctor = doctor;
        this.percentage = percentage;
    }

    public Long getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public BigDecimal getPercentage() {
        return percentage;
    }

    public void setPercentage(BigDecimal percentage) {
        this.percentage = percentage;
    }

    public boolean isActive() {
        return active;
    }
}
