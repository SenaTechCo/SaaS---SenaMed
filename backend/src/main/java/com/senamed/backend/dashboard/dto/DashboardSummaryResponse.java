package com.senamed.backend.dashboard.dto;

import com.senamed.backend.appointment.dto.AppointmentResponse;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@code activeDoctorCount}, {@code pendingReceivablesTotal} and {@code paidThisMonthTotal} are
 * only populated for ADMIN callers - they are clinic-wide information that has no meaning for a
 * DOCTOR viewing their own agenda summary, so they come back {@code null} in that case rather than
 * a doctor-scoped figure pretending to be a clinic total.
 */
public record DashboardSummaryResponse(
        long todayCount,
        List<AppointmentResponse> upcoming,
        Long activeDoctorCount,
        BigDecimal pendingReceivablesTotal,
        BigDecimal paidThisMonthTotal) {
}
