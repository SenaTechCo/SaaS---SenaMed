package com.senamed.backend.user;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.doctor.Doctor;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
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

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

/**
 * {@code User} intentionally does NOT extend
 * {@link com.senamed.backend.tenant.TenantScopedEntity}, even though it has a {@code clinic_id}
 * column: authentication (login by email) must look up users *across all clinics* - email is
 * globally unique - so the row cannot be pre-filtered by "current tenant" (there is no
 * authenticated tenant yet at that point in the request).
 *
 * <p>Once a user is loaded, everything else about them (id, clinicId, role) is carried inside the
 * JWT (see {@link com.senamed.backend.security.JwtService}), so no further tenant-filtered lookup
 * of users is needed in this vertical slice.</p>
 *
 * <p>Future tenant-scoped entities (doctors, appointments, ...) that don't need this kind of
 * global lookup should extend {@link com.senamed.backend.tenant.TenantScopedEntity} instead.</p>
 */
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UserRole role = UserRole.ADMIN;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id", nullable = true, updatable = false)
    private Doctor doctor;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "user_permissions", joinColumns = @JoinColumn(name = "user_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "permission")
    private Set<Permission> permissions = new HashSet<>();

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected User() {
        // JPA
    }

    public User(Clinic clinic, String name, String email, String passwordHash, UserRole role) {
        this(clinic, name, email, passwordHash, role, null);
    }

    public User(Clinic clinic, String name, String email, String passwordHash, UserRole role, Doctor doctor) {
        this.clinic = clinic;
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.role = role;
        this.doctor = doctor;
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

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserRole getRole() {
        return role;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Permission> getPermissions() {
        return permissions;
    }

    /**
     * @return every permission this user actually has, for enforcement purposes.
     * {@link UserRole#ADMIN} users always get every {@link Permission} regardless of what (if
     * anything) is stored in {@code permissions} - this is a hard security invariant: the clinic
     * owner's ADMIN account must never lose access through the permission system, and
     * {@code permissions} is never even read for an ADMIN user.
     */
    public Set<Permission> effectivePermissions() {
        return role == UserRole.ADMIN ? Set.of(Permission.values()) : permissions;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    /** Replaces the whole permission set - used by "save the whole edit form" callers. */
    public void setPermissions(Set<Permission> permissions) {
        this.permissions = new HashSet<>(permissions);
    }
}
