package com.senamed.backend.billing.dto;

import com.senamed.backend.billing.Preapproval;
import com.senamed.backend.billing.PreapprovalStatus;

import java.time.Instant;

public record PreapprovalResponse(
        Long id,
        Long planId,
        String planName,
        PreapprovalStatus status,
        Integer periodMonths,
        Instant currentPeriodStart,
        Instant currentPeriodEnd) {

    public static PreapprovalResponse from(Preapproval preapproval) {
        return new PreapprovalResponse(
                preapproval.getId(),
                preapproval.getPlan().getId(),
                preapproval.getPlan().getName(),
                preapproval.getStatus(),
                preapproval.getPeriodMonths(),
                preapproval.getCurrentPeriodStart(),
                preapproval.getCurrentPeriodEnd());
    }
}
