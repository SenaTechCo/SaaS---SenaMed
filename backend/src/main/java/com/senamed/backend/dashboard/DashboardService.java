package com.senamed.backend.dashboard;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.AppointmentStatus;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.dashboard.dto.DashboardReportsResponse;
import com.senamed.backend.dashboard.dto.DashboardSummaryResponse;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.finance.CommissionCalculator;
import com.senamed.backend.finance.CommissionConfig;
import com.senamed.backend.finance.CommissionConfigRepository;
import com.senamed.backend.finance.Receivable;
import com.senamed.backend.finance.ReceivableRepository;
import com.senamed.backend.finance.ReceivableService;
import com.senamed.backend.finance.ReceivableStatus;
import com.senamed.backend.finance.dto.FinanceSummaryResponse;
import com.senamed.backend.security.AuthenticatedUser;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

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
    private final ReceivableRepository receivableRepository;
    private final CommissionConfigRepository commissionConfigRepository;

    public DashboardService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            ClinicRepository clinicRepository,
            ReceivableService receivableService,
            ReceivableRepository receivableRepository,
            CommissionConfigRepository commissionConfigRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.clinicRepository = clinicRepository;
        this.receivableService = receivableService;
        this.receivableRepository = receivableRepository;
        this.commissionConfigRepository = commissionConfigRepository;
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

    /**
     * Backs the clinic reports screen (KAN-102, ADMIN-only): a daily-granularity time series of
     * attendance/billing figures over the trailing {@code days} days (inclusive of today), plus
     * the period totals. Mirrors {@link ReceivableService#summary}'s in-Java aggregation approach
     * - clinics are small enough at this stage that this is not a performance concern.
     */
    @Transactional(readOnly = true)
    public DashboardReportsResponse getReports(int days) {
        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));
        ZoneId clinicZone = ZoneId.of(clinic.getTimezone());

        LocalDate today = LocalDate.now(clinicZone);
        LocalDate rangeStart = today.minusDays(days - 1L);

        LocalDateTime rangeStartDateTime = rangeStart.atStartOfDay();
        LocalDateTime rangeEndDateTime = today.plusDays(1).atStartOfDay();
        Instant rangeStartInstant = rangeStart.atStartOfDay(clinicZone).toInstant();
        Instant rangeEndInstant = today.plusDays(1).atStartOfDay(clinicZone).toInstant();

        List<Appointment> appointments = appointmentRepository
                .findAllByClinicIdAndStartsAtBetween(clinicId, rangeStartDateTime, rangeEndDateTime);
        List<Receivable> receivables = receivableRepository
                .findAllByClinicIdAndCreatedAtBetween(clinicId, rangeStartInstant, rangeEndInstant);

        List<DashboardReportsResponse.DailyPoint> dailySeries = new ArrayList<>();
        BigDecimal grossRevenue = BigDecimal.ZERO;
        for (int i = 0; i < days; i++) {
            LocalDate day = rangeStart.plusDays(i);

            long attended = countAppointmentsOnDay(appointments, day, AppointmentStatus.ATTENDED);
            long cancelled = countAppointmentsOnDay(appointments, day, AppointmentStatus.CANCELLED);
            long noShow = countAppointmentsOnDay(appointments, day, AppointmentStatus.NO_SHOW);

            BigDecimal received = receivables.stream()
                    .filter(r -> r.getStatus() == ReceivableStatus.PAID)
                    .filter(r -> r.getPaidAt() != null && r.getPaidAt().atZone(clinicZone).toLocalDate().isEqual(day))
                    .map(Receivable::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            BigDecimal receivableAmount = receivables.stream()
                    .filter(r -> r.getCreatedAt().atZone(clinicZone).toLocalDate().isEqual(day))
                    .map(Receivable::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            grossRevenue = grossRevenue.add(received);
            dailySeries.add(new DashboardReportsResponse.DailyPoint(day, received, receivableAmount, attended, cancelled, noShow));
        }

        long attendedCount = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.ATTENDED).count();
        long cancelledCount = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.CANCELLED).count();
        long noShowCount = appointments.stream().filter(a -> a.getStatus() == AppointmentStatus.NO_SHOW).count();

        BigDecimal directCost = BigDecimal.ZERO;
        for (Doctor doctor : doctorRepository.findAllByClinicIdAndActiveTrueOrderByNameAsc(clinicId)) {
            Optional<CommissionConfig> config = commissionConfigRepository.findByClinicIdAndDoctorId(clinicId, doctor.getId());
            if (config.isEmpty()) {
                continue;
            }
            BigDecimal doctorBilled = receivables.stream()
                    .filter(r -> r.getDoctor().getId().equals(doctor.getId()))
                    .map(Receivable::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            directCost = directCost.add(CommissionCalculator.apply(doctorBilled, config.get().getPercentage()));
        }

        BigDecimal grossProfit = grossRevenue.subtract(directCost);

        return new DashboardReportsResponse(
                dailySeries, attendedCount, cancelledCount, noShowCount, grossRevenue, directCost, grossProfit);
    }

    private long countAppointmentsOnDay(List<Appointment> appointments, LocalDate day, AppointmentStatus status) {
        return appointments.stream()
                .filter(a -> a.getStatus() == status)
                .filter(a -> a.getStartsAt().toLocalDate().isEqual(day))
                .count();
    }
}
