package com.senamed.backend.billing.mercadopago.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;

/**
 * Mirrors the JSON body expected by Mercado Pago's {@code POST /preapproval}. Field shape should
 * be re-verified against MP's real Preapproval API docs at first real (sandbox) integration - no
 * sandbox credentials exist yet to confirm empirically, same posture as {@link PreferenceRequest}.
 */
public record PreapprovalRequest(
        String reason,
        @JsonProperty("external_reference") String externalReference,
        @JsonProperty("payer_email") String payerEmail,
        @JsonProperty("back_url") String backUrl,
        @JsonProperty("notification_url") String notificationUrl,
        @JsonProperty("auto_recurring") AutoRecurring autoRecurring) {

    public record AutoRecurring(
            Integer frequency,
            @JsonProperty("frequency_type") String frequencyType,
            @JsonProperty("transaction_amount") BigDecimal transactionAmount,
            @JsonProperty("currency_id") String currencyId) {
    }
}
