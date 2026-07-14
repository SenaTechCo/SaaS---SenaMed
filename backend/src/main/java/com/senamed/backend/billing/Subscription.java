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
 * One row per checkout attempt ("checkout avulso" - a single non-recurring Mercado Pago payment for
 * a chosen period, RF-020). A clinic's current subscription is simply the most recent row - see
 * {@code SubscriptionRepository.findFirstByClinicIdOrderByCreatedAtDesc}.
 */
@Entity
@Table(name = "subscriptions")
public class Subscription {

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
    private SubscriptionStatus status = SubscriptionStatus.PENDING;

    @Column(name = "period_months", nullable = false, updatable = false)
    private Integer periodMonths;

    @Column(name = "mp_preference_id")
    private String mpPreferenceId;

    @Column(name = "mp_payment_id")
    private String mpPaymentId;

    @Column(name = "current_period_start")
    private Instant currentPeriodStart;

    @Column(name = "current_period_end")
    private Instant currentPeriodEnd;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected Subscription() {
        // JPA
    }

    public Subscription(Clinic clinic, Plan plan, Integer periodMonths) {
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

    public SubscriptionStatus getStatus() {
        return status;
    }

    public Integer getPeriodMonths() {
        return periodMonths;
    }

    public String getMpPreferenceId() {
        return mpPreferenceId;
    }

    public void setMpPreferenceId(String mpPreferenceId) {
        this.mpPreferenceId = mpPreferenceId;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
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

    public void markApproved(String paymentId, Instant periodStart, Instant periodEnd) {
        this.status = SubscriptionStatus.APPROVED;
        this.mpPaymentId = paymentId;
        this.currentPeriodStart = periodStart;
        this.currentPeriodEnd = periodEnd;
    }

    public void markRejected(String paymentId) {
        this.status = SubscriptionStatus.REJECTED;
        this.mpPaymentId = paymentId;
    }
}
