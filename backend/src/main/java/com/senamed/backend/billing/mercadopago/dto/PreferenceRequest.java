package com.senamed.backend.billing.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Mirrors the JSON body expected by Mercado Pago's {@code POST /checkout/preferences}. */
public record PreferenceRequest(
        List<Item> items,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("notification_url") String notificationUrl,
        @JsonProperty("back_urls") BackUrls backUrls,
        @JsonProperty("auto_return") String autoReturn) {

    public record Item(
            String title,
            Integer quantity,
            @JsonProperty("unit_price") BigDecimal unitPrice,
            @JsonProperty("currency_id") String currencyId) {
    }

    public record BackUrls(String success, String failure, String pending) {
    }
}
