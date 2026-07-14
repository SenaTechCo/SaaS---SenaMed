package com.senamed.backend.billing.mercadopago;

import java.math.BigDecimal;

public record CreatePreapprovalCommand(
        String reason,
        BigDecimal transactionAmount,
        Integer frequencyMonths,
        String payerEmail,
        String externalReference,
        String notificationUrl,
        String backUrl) {
}
