package com.senamed.backend.billing.dto;

import com.senamed.backend.billing.Plan;

import java.math.BigDecimal;

public record PlanResponse(Long id, String name, BigDecimal priceAmount, Integer maxDoctors) {

    public static PlanResponse from(Plan plan) {
        return new PlanResponse(plan.getId(), plan.getName(), plan.getPriceAmount(), plan.getMaxDoctors());
    }
}
