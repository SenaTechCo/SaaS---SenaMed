package com.senamed.backend.patient;

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
import java.time.LocalDate;

/**
 * Clinic-scoped patient registry (Fase 9). Mirrors {@link com.senamed.backend.doctor.Doctor}'s
 * tenant-scoping pattern exactly - see {@link TenantScopedEntity}'s javadoc for the Hibernate
 * {@code @Filter} mechanism this class inherits.
 *
 * <p>Deliberately independent of {@code Appointment} for now: appointments still carry their own
 * {@code patientName}/{@code patientEmail}/{@code patientPhone} rather than a {@code patient_id}
 * FK - linking them is left for a future phase.</p>
 */
@Entity
@Table(name = "patients")
public class Patient extends TenantScopedEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @Column(nullable = false)
    private String name;

    @Column(name = "social_name")
    private String socialName;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    private String sex;

    private String cpf;

    private String email;

    private String phone;

    @Column(name = "zip_code")
    private String zipCode;

    private String street;

    private String number;

    private String complement;

    private String neighborhood;

    private String city;

    private String state;

    @Column(name = "referral_source")
    private String referralSource;

    private String notes;

    @Column(name = "lgpd_consent", nullable = false)
    private boolean lgpdConsent = false;

    @Column(name = "lgpd_consent_at")
    private Instant lgpdConsentAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Patient() {
        // JPA
    }

    public Patient(Clinic clinic, String name) {
        this.clinic = clinic;
        this.name = name;
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

    public String getSocialName() {
        return socialName;
    }

    public void setSocialName(String socialName) {
        this.socialName = socialName;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getCpf() {
        return cpf;
    }

    public void setCpf(String cpf) {
        this.cpf = cpf;
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

    public String getZipCode() {
        return zipCode;
    }

    public void setZipCode(String zipCode) {
        this.zipCode = zipCode;
    }

    public String getStreet() {
        return street;
    }

    public void setStreet(String street) {
        this.street = street;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getComplement() {
        return complement;
    }

    public void setComplement(String complement) {
        this.complement = complement;
    }

    public String getNeighborhood() {
        return neighborhood;
    }

    public void setNeighborhood(String neighborhood) {
        this.neighborhood = neighborhood;
    }

    public String getCity() {
        return city;
    }

    public void setCity(String city) {
        this.city = city;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public String getReferralSource() {
        return referralSource;
    }

    public void setReferralSource(String referralSource) {
        this.referralSource = referralSource;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public boolean isLgpdConsent() {
        return lgpdConsent;
    }

    public Instant getLgpdConsentAt() {
        return lgpdConsentAt;
    }

    /** Records consent the same way {@code Appointment.lgpdConsentAt} does: server-set, only moves forward. */
    public void setLgpdConsent(boolean lgpdConsent) {
        this.lgpdConsent = lgpdConsent;
        if (lgpdConsent && this.lgpdConsentAt == null) {
            this.lgpdConsentAt = Instant.now();
        }
    }

    public boolean isActive() {
        return active;
    }

    /** Soft delete: DELETE /api/patients/{id} never removes the row, only deactivates it. */
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
