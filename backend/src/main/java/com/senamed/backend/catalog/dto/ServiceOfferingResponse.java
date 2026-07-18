package com.senamed.backend.catalog.dto;

import com.senamed.backend.catalog.ServiceOffering;

import java.math.BigDecimal;
import java.time.Instant;

public record ServiceOfferingResponse(
        Long id,
        String name,
        String description,
        int durationMinutes,
        BigDecimal price,
        boolean active,
        Instant createdAt) {

    public static ServiceOfferingResponse from(ServiceOffering serviceOffering) {
        return new ServiceOfferingResponse(
                serviceOffering.getId(),
                serviceOffering.getName(),
                serviceOffering.getDescription(),
                serviceOffering.getDurationMinutes(),
                serviceOffering.getPrice(),
                serviceOffering.isActive(),
                serviceOffering.getCreatedAt());
    }
}
