package com.senamed.backend.dashboard.dto;

import com.senamed.backend.appointment.dto.AppointmentResponse;

import java.util.List;

/**
 * {@code activeDoctorCount} is only populated for ADMIN callers - it is clinic-wide information
 * that has no meaning for a DOCTOR viewing their own agenda summary, so it comes back {@code null}
 * in that case rather than a doctor-scoped count of one thing pretending to be a clinic total.
 */
public record DashboardSummaryResponse(
        long todayCount,
        List<AppointmentResponse> upcoming,
        Long activeDoctorCount) {
}
