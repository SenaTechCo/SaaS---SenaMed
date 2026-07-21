package com.senamed.backend.appointment;

import com.senamed.backend.catalog.ServiceOffering;
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
 * A single service line on an {@link Appointment} (KAN-103) - an appointment can carry multiple of
 * these, each snapshotting the {@link ServiceOffering}'s price at the moment it was added (mirrors
 * {@code Appointment}'s pre-existing single-service price-snapshot behavior, just per line now).
 *
 * <p>Always reached through its parent {@link Appointment} ({@code appointment.getLineItems()}), so
 * unlike {@link com.senamed.backend.patient.Patient}/{@link ServiceOffering} it does not extend
 * {@code TenantScopedEntity} - there is no direct clinic-scoped query path to protect.</p>
 */
@Entity
@Table(name = "appointment_line_items")
public class AppointmentLineItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "appointment_id", nullable = false, updatable = false)
    private Appointment appointment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "service_id", nullable = false, updatable = false)
    private ServiceOffering service;

    @Column(nullable = false)
    private int quantity;

    @Column(name = "unit_price", nullable = false)
    private BigDecimal unitPrice;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected AppointmentLineItem() {
        // JPA
    }

    public AppointmentLineItem(Appointment appointment, ServiceOffering service, int quantity) {
        this.appointment = appointment;
        this.service = service;
        this.quantity = quantity;
        this.unitPrice = service.getPrice();
    }

    public Long getId() {
        return id;
    }

    public Appointment getAppointment() {
        return appointment;
    }

    public ServiceOffering getService() {
        return service;
    }

    public int getQuantity() {
        return quantity;
    }

    public BigDecimal getUnitPrice() {
        return unitPrice;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public BigDecimal getLineTotal() {
        return unitPrice.multiply(BigDecimal.valueOf(quantity));
    }
}
