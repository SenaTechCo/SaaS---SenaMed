package com.senamed.backend.catalog;

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

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Clinic-scoped service/procedure catalog entry (Catalogo de Servicos). Mirrors
 * {@link com.senamed.backend.patient.Patient}'s tenant-scoping pattern exactly - see
 * {@link TenantScopedEntity}'s javadoc for the Hibernate {@code @Filter} mechanism this class
 * inherits.
 *
 * <p>Deliberately independent of {@code Appointment} for now: there is no link between a service
 * offering and an appointment - that is left for a future phase.</p>
 */
@Entity
@Table(name = "service_offerings")
public class ServiceOffering extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @Column(nullable = false)
    private String name;

    private String description;

    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    @Column(nullable = false)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ServiceOffering() {
        // JPA
    }

    public ServiceOffering(Clinic clinic, String name, int durationMinutes, BigDecimal price) {
        this.clinic = clinic;
        this.name = name;
        this.durationMinutes = durationMinutes;
        this.price = price;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public void setDurationMinutes(int durationMinutes) {
        this.durationMinutes = durationMinutes;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public boolean isActive() {
        return active;
    }

    /** Soft delete: DELETE /api/services/{id} never removes the row, only deactivates it. */
    public void deactivate() {
        this.active = false;
    }

    public void restore() {
        this.active = true;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
