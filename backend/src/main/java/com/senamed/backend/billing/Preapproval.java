package com.senamed.backend.billing;

import com.senamed.backend.clinic.Clinic;
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

import java.time.Instant;

/**
 * A clinic's recurring billing relationship via Mercado Pago Preapproval (KAN-76). Unlike
 * {@link Subscription} (one row per checkout attempt, append-only), this is a single persistent,
 * mutable row per clinic that tracks the one long-lived Mercado Pago preapproval object and gets
 * updated in place on each status change / recurring charge. Coexists with the "checkout avulso"
 * flow - a clinic uses one mechanism or the other.
 */
@Entity
@Table(name = "preapprovals")
public class Preapproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "clinic_id", nullable = false, updatable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false, updatable = false)
    private Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PreapprovalStatus status = PreapprovalStatus.PENDING;

    @Column(name = "period_months", nullable = false, updatable = false)
    private Integer periodMonths;

    @Column(name = "mp_preapproval_id")
    private String mpPreapprovalId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Preapproval() {
        // JPA
    }

    public Preapproval(Clinic clinic, Plan plan, Integer periodMonths) {
        this.clinic = clinic;
        this.plan = plan;
        this.periodMonths = periodMonths;
    }

    public Long getId() {
        return id;
    }

    public Clinic getClinic() {
        return clinic;
    }

    public Plan getPlan() {
        return plan;
    }

    public PreapprovalStatus getStatus() {
        return status;
    }

    public Integer getPeriodMonths() {
        return periodMonths;
    }

    public String getMpPreapprovalId() {
        return mpPreapprovalId;
    }

    public void setMpPreapprovalId(String mpPreapprovalId) {
        this.mpPreapprovalId = mpPreapprovalId;
        this.updatedAt = Instant.now();
    }

    public Instant getCurrentPeriodStart() {
        return currentPeriodStart;
    }

    public Instant getCurrentPeriodEnd() {
        return currentPeriodEnd;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    /** Status-only transition (from the {@code subscription_preapproval} webhook) - no period side effects. */
    public void applyStatus(PreapprovalStatus status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }

    /** Applied when a recurring charge is confirmed (from the {@code subscription_authorized_payment} webhook). */
    public void registerCharge(Instant periodStart, Instant periodEnd) {
        this.status = PreapprovalStatus.AUTHORIZED;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
        this.updatedAt = Instant.now();
    }

    public void markCancelled() {
        this.status = PreapprovalStatus.CANCELLED;
        this.updatedAt = Instant.now();
    }
}
