package com.senamed.backend.catalog.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ServiceOfferingUpdateRequest(
        @NotBlank(message = "name is required") String name,
        String description,
        @NotNull(message = "durationMinutes is required")
        @Min(value = 5, message = "durationMinutes must be at least 5") Integer durationMinutes,
        @NotNull(message = "price is required")
        @DecimalMin(value = "0.0", message = "price must not be negative") BigDecimal price) {
}
