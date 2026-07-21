package com.senamed.backend.appointment.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record ServiceItemRequest(
        @NotNull(message = "serviceId is required") Long serviceId,
        @NotNull(message = "quantity is required") @Min(value = 1, message = "quantity must be at least 1") Integer quantity) {
}
