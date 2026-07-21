package com.senamed.backend.finance.dto;

import java.math.BigDecimal;

public record CommissionCalculationResponse(
        Long doctorId, int year, int month, BigDecimal percentage, BigDecimal totalBilled, BigDecimal commissionAmount) {
}
