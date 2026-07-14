package com.senamed.backend.billing.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors (the relevant subset of) Mercado Pago's {@code GET /v1/payments/{id}} response. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PaymentApiResponse(String status, @JsonProperty("external_reference") String externalReference) {
}
