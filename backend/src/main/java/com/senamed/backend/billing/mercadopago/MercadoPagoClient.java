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
     * webhook notification payloads are never trusted on their own. Also reused to confirm each
     * individual recurring charge of a Preapproval, since MP's {@code subscription_authorized_payment}
     * notifications reference a regular payment id fetchable the same way.
     */
    PaymentResult getPayment(String paymentId);

    /** Creates a recurring Preapproval (a hosted, redirect-based recurring-authorization page). */
    PreapprovalResult createPreapproval(CreatePreapprovalCommand command);

    /** Re-fetches a preapproval's authoritative status directly from Mercado Pago's API (RN-014). */
    PreapprovalStatusResult getPreapproval(String preapprovalId);

    /** Cancels a preapproval directly at Mercado Pago. */
    PreapprovalStatusResult cancelPreapproval(String preapprovalId);
}
