package com.senamed.backend.billing.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Mirrors (the relevant subset of) Mercado Pago's preapproval create/get/cancel responses. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PreapprovalApiResponse(
        String id,
        String status,
        @JsonProperty("init_point") String initPoint,
        @JsonProperty("external_reference") String externalReference) {
}
