package com.senamed.backend.billing.dto;

import com.senamed.backend.billing.Subscription;
import com.senamed.backend.billing.SubscriptionStatus;

import java.time.Instant;

public record SubscriptionResponse(
        Long id,
        Long planId,
        String planName,
        SubscriptionStatus status,
        Integer periodMonths,
        Instant currentPeriodStart,
        Instant currentPeriodEnd) {

    public static SubscriptionResponse from(Subscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getPlan().getId(),
                subscription.getPlan().getName(),
                subscription.getStatus(),
                subscription.getPeriodMonths(),
                subscription.getCurrentPeriodStart(),
                subscription.getCurrentPeriodEnd());
    }
}
