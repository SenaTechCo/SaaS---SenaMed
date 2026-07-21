package com.senamed.backend.finance.dto;

import java.math.BigDecimal;

public record FinanceSummaryResponse(BigDecimal pendingTotal, BigDecimal paidThisMonthTotal) {
}
