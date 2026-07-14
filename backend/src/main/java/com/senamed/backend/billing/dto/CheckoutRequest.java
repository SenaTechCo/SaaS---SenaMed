package com.senamed.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

public record CheckoutRequest(@NotNull Long planId, @NotNull Integer periodMonths) {
}
