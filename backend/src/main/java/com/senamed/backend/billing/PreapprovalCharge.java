package com.senamed.backend.billing;

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
 * One row per confirmed recurring charge ({@code subscription_authorized_payment} webhook) - the
 * idempotency backstop for Preapproval, analogous to {@code Subscription.mpPaymentId}'s UNIQUE
 * constraint but scoped per-charge instead of per-checkout-attempt, since a preapproval has many
 * charges over its lifetime.
 */
@Entity
@Table(name = "preapproval_charges")
public class PreapprovalCharge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preapproval_id", nullable = false, updatable = false)
    private Preapproval preapproval;

    @Column(name = "mp_payment_id", nullable = false, updatable = false)
    private String mpPaymentId;

    @Column(nullable = false, updatable = false)
    private String status;

    @Column(name = "charged_at", nullable = false, updatable = false)
    private Instant chargedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected PreapprovalCharge() {
        // JPA
    }

    public PreapprovalCharge(Preapproval preapproval, String mpPaymentId, String status, Instant chargedAt) {
        this.preapproval = preapproval;
        this.mpPaymentId = mpPaymentId;
        this.status = status;
        this.chargedAt = chargedAt;
    }

    public Long getId() {
        return id;
    }

    public Preapproval getPreapproval() {
        return preapproval;
    }

    public String getMpPaymentId() {
        return mpPaymentId;
    }

    public String getStatus() {
        return status;
    }

    public Instant getChargedAt() {
        return chargedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
