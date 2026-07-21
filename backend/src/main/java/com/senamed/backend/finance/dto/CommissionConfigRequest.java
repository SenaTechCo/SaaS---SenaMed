package com.senamed.backend.finance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CommissionConfigRequest(
        @NotNull(message = "percentage is required")
        @DecimalMin(value = "0.0", message = "percentage must not be negative")
        @DecimalMax(value = "100.0", message = "percentage must not exceed 100") BigDecimal percentage) {
}
