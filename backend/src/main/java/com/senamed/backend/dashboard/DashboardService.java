package com.senamed.backend.dashboard;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.AppointmentStatus;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.dashboard.dto.DashboardSummaryResponse;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.finance.ReceivableService;
import com.senamed.backend.finance.dto.FinanceSummaryResponse;
import com.senamed.backend.security.AuthenticatedUser;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Backs the dashboard home page (KAN-98) - the first screen a clinic sees after logging in.
 * Reuses the same appointment listings already queried for {@code AppointmentService}/
 * {@code DoctorSelfController} rather than adding bespoke repository queries; clinics are small
 * enough at this stage that filtering/limiting in Java is not a performance concern.
 */
@Service
public class DashboardService {

    /** How many future appointments to surface as "próximos agendamentos". */
    private static final int UPCOMING_LIMIT = 5;

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final ClinicRepository clinicRepository;
    private final ReceivableService receivableService;

    public DashboardService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            ClinicRepository clinicRepository,
            ReceivableService receivableService) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.clinicRepository = clinicRepository;
        this.receivableService = receivableService;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        AuthenticatedUser currentUser = TenantContext.currentUser();
        Long clinicId = currentUser.clinicId();
        Long doctorId = currentUser.doctorId();

        List<Appointment> appointments = doctorId != null
                ? appointmentRepository.findAllByClinicIdAndDoctorIdOrderByStartsAtAsc(clinicId, doctorId)
                : appointmentRepository.findAllByClinicIdOrderByStartsAtAsc(clinicId);

        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));
        ZoneId clinicZone = ZoneId.of(clinic.getTimezone());
        LocalDate today = LocalDate.now(clinicZone);
        LocalDateTime now = LocalDateTime.now(clinicZone);

        long todayCount = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(appointment -> appointment.getStartsAt().toLocalDate().isEqual(today))
                .count();

        List<AppointmentResponse> upcoming = appointments.stream()
                .filter(appointment -> appointment.getStatus() == AppointmentStatus.CONFIRMED)
                .filter(appointment -> appointment.getStartsAt().isAfter(now))
                .limit(UPCOMING_LIMIT)
                .map(AppointmentResponse::from)
                .toList();

        Long activeDoctorCount = doctorId == null ? doctorRepository.countByClinicIdAndActiveTrue(clinicId) : null;

        BigDecimal pendingReceivablesTotal = null;
        BigDecimal paidThisMonthTotal = null;
        if (doctorId == null) {
            FinanceSummaryResponse financeSummary = receivableService.summary();
            pendingReceivablesTotal = financeSummary.pendingTotal();
            paidThisMonthTotal = financeSummary.paidThisMonthTotal();
        }

        return new DashboardSummaryResponse(todayCount, upcoming, activeDoctorCount, pendingReceivablesTotal, paidThisMonthTotal);
    }
}
