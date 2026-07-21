package com.senamed.backend.finance;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared "amount * percentage / 100, rounded HALF_UP to 2 decimals" math used to turn a doctor's
 * billed total into a commission figure - reused by {@link CommissionService#calculate} and by
 * {@code com.senamed.backend.dashboard.DashboardService#getReports}'s direct-cost calculation
 * (KAN-102), which needs the exact same per-doctor math.
 */
public final class CommissionCalculator {

    private CommissionCalculator() {
    }

    public static BigDecimal apply(BigDecimal amount, BigDecimal percentage) {
        return amount.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
