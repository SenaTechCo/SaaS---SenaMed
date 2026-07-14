package com.senamed.backend.billing.webhook;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** Mirrors Mercado Pago's webhook notification body: {@code { "type": "payment", "data": { "id": "..." } } }. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MercadoPagoWebhookPayload(String type, Data data) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String id) {
    }
}
