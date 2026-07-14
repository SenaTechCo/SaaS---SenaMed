package com.senamed.backend.billing.dto;

import jakarta.validation.constraints.NotNull;

public record CreatePreapprovalRequest(@NotNull Long planId, @NotNull Integer periodMonths) {
}
