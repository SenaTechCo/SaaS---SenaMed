package com.senamed.backend.billing.mercadopago;

/**
 * Thin abstraction over Mercado Pago's REST API - kept as an interface (rather than a concrete
 * class directly injected) so tests can {@code @MockitoBean} it and never make a real network call,
 * mirroring how Fase 4 mocks {@code JavaMailSender}.
 */
public interface MercadoPagoClient {

    /** Creates a Checkout Pro preference (a hosted, redirect-based payment page). */
    PreferenceResult createPreference(CreatePreferenceCommand command);

    /**
     * Re-fetches a payment's authoritative status directly from Mercado Pago's API (RN-014) -
     * webhook notification payloads are never trusted on their own.
     */
    PaymentResult getPayment(String paymentId);
}
