package com.senamed.backend.billing.dto;

public record CheckoutResponse(Long subscriptionId, String checkoutUrl) {
}
