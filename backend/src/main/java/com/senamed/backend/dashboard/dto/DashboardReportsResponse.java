package com.senamed.backend.dashboard.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Backs the clinic reports screen (KAN-102, ADMIN-only): a daily-granularity time series plus the
 * period totals ({@code grossRevenue} = actually received, {@code directCost} = doctor commissions
 * on billed services, {@code grossProfit} = the difference between the two).
 */
public record DashboardReportsResponse(
        List<DailyPoint> dailySeries,
        long attendedCount,
        long cancelledCount,
        long noShowCount,
        BigDecimal grossRevenue,
        BigDecimal directCost,
        BigDecimal grossProfit) {

    /**
     * One day of the series. {@code received} is the amount of receivables actually paid that
     * day; {@code receivable} is the amount that became a receivable that day (regardless of
     * whether it has been paid yet).
     */
    public record DailyPoint(
            LocalDate date,
            BigDecimal received,
            BigDecimal receivable,
            long attended,
            long cancelled,
            long noShow) {
    }
}
