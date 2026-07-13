package com.senamed.backend.clinic;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Note: {@code Clinic} itself is the tenant root, not a tenant-scoped entity, so it does NOT
 * extend {@link com.senamed.backend.tenant.TenantScopedEntity}. Entities that belong to a clinic
 * (e.g. the future {@code doctors} table) should extend that base class instead - see its
 * javadoc for the pattern.
 */
@Entity
@Table(name = "clinics")
public class Clinic {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClinicStatus status = ClinicStatus.TRIAL;

    @Column(nullable = false)
    private String timezone = "America/Sao_Paulo";

    private String description;

    private String phone;

    private String email;

    @Column(name = "trial_ends_at")
    private Instant trialEndsAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Clinic() {
        // JPA
    }

    public Clinic(String name, String slug, Instant trialEndsAt) {
        this.name = name;
        this.slug = slug;
        this.trialEndsAt = trialEndsAt;
        this.status = ClinicStatus.TRIAL;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSlug() {
        return slug;
    }

    public ClinicStatus getStatus() {
        return status;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public Instant getTrialEndsAt() {
        return trialEndsAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
