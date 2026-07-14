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

    /**
     * Simple placeholder for the plan/subscription limit until Fase 5 implements real plans and
     * billing. RN-015 (max active doctors per clinic) is validated against this column in the
     * meantime - always server-side, see {@code DoctorService.create}. Not editable via
     * PUT /api/clinics/me; it is plan-controlled.
     */
    @Column(name = "max_doctors", nullable = false)
    private Integer maxDoctors = 3;

    @Column(name = "logo_url")
    private String logoUrl;

    @Column(name = "cover_image_url")
    private String coverImageUrl;

    @Column(name = "primary_color", length = 7)
    private String primaryColor;

    @Column(name = "secondary_color", length = 7)
    private String secondaryColor;

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

    public void setStatus(ClinicStatus status) {
        this.status = status;
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

    public Integer getMaxDoctors() {
        return maxDoctors;
    }

    public void setMaxDoctors(Integer maxDoctors) {
        this.maxDoctors = maxDoctors;
    }

    public String getLogoUrl() {
        return logoUrl;
    }

    public void setLogoUrl(String logoUrl) {
        this.logoUrl = logoUrl;
    }

    public String getCoverImageUrl() {
        return coverImageUrl;
    }

    public void setCoverImageUrl(String coverImageUrl) {
        this.coverImageUrl = coverImageUrl;
    }

    public String getPrimaryColor() {
        return primaryColor;
    }

    public void setPrimaryColor(String primaryColor) {
        this.primaryColor = primaryColor;
    }

    public String getSecondaryColor() {
        return secondaryColor;
    }

    public void setSecondaryColor(String secondaryColor) {
        this.secondaryColor = secondaryColor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
