package com.senamed.backend.billing.mercadopago;

/** Thrown when Mercado Pago's own API is unreachable or returns an error - genuinely unresolved. */
public class MercadoPagoIntegrationException extends RuntimeException {

    public MercadoPagoIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
