package com.senamed.backend.billing.mercadopago;

/** {@code status} is Mercado Pago's raw payment status string (e.g. "approved", "rejected", "pending"). */
public record PaymentResult(String paymentId, String status, String externalReference) {
}
