package com.senamed.backend.billing.mercadopago.dto;

/** Body for {@code PUT /preapproval/{id}} - only {@code status} is used here (to cancel). */
public record PreapprovalStatusUpdateRequest(String status) {
}
