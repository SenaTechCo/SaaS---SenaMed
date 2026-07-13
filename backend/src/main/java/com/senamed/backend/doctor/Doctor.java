package com.senamed.backend.doctor;

import com.senamed.backend.clinic.Clinic;
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

import java.time.Instant;

/**
 * First tenant-scoped entity beyond {@code User} (Fase 2) - see
 * {@link com.senamed.backend.tenant.TenantScopedEntity} for the Hibernate {@code @Filter}
 * mechanism this class inherits (enabled per-request by {@code TenantFilterInterceptor}).
 *
 * <p>The inherited {@code clinicId} column is mapped read-only ({@code insertable = false},
 * {@code updatable = false}) purely so the Hibernate filter/derived queries (e.g.
 * {@code findAllByClinicId}) can reference it. The actual FK is written through the
 * {@link #clinic} association below, which owns the same {@code clinic_id} column for
 * insert.</p>
 *
 * <p>As recommended by {@link com.senamed.backend.tenant.TenantScopedEntity}'s javadoc,
 * {@link DoctorRepository} additionally scopes lookups explicitly by clinic id (defense in
 * depth) - do not rely on the Hibernate filter alone.</p>
 */
@Entity
@Table(name = "doctors")
public class Doctor extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @Column(nullable = false)
    private String name;

    private String specialty;

    private String email;

    private String phone;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Doctor() {
        // JPA
    }

    public Doctor(Clinic clinic, String name, String specialty, String email, String phone) {
        this.clinic = clinic;
        this.name = name;
        this.specialty = specialty;
        this.email = email;
        this.phone = phone;
    }

    public Long getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSpecialty() {
        return specialty;
    }

    public void setSpecialty(String specialty) {
        this.specialty = specialty;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isActive() {
        return active;
    }

    /** Soft delete: DELETE /api/doctors/{id} never removes the row, only deactivates it. */
    public void deactivate() {
        this.active = false;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
