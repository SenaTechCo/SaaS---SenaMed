package com.senamed.backend.billing.mercadopago;

import java.math.BigDecimal;

public record CreatePreferenceCommand(
        String title,
        BigDecimal unitPrice,
        String externalReference,
        String notificationUrl,
        String successUrl,
        String failureUrl,
        String pendingUrl) {
}
