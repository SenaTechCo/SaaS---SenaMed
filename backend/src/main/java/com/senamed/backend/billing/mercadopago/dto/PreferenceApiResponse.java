package com.senamed.backend.billing.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mirrors (the relevant subset of) Mercado Pago's {@code checkout/preferences} response.
 * {@code sandboxInitPoint} is populated when the access token is a {@code TEST-...} sandbox token.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreferenceApiResponse(
        String id,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("sandbox_init_point") String sandboxInitPoint) {
}
