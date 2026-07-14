package com.senamed.backend.billing.mercadopago;

/** {@code status} is Mercado Pago's raw preapproval status string (e.g. "pending", "authorized", "paused", "cancelled"). */
public record PreapprovalStatusResult(String preapprovalId, String status, String externalReference) {
}
