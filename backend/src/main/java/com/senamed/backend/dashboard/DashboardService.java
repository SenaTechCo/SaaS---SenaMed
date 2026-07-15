package com.senamed.backend.dashboard;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.AppointmentStatus;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.dashboard.dto.DashboardSummaryResponse;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.security.AuthenticatedUser;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
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

    public DashboardService(AppointmentRepository appointmentRepository, DoctorRepository doctorRepository) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional(readOnly = true)
    public DashboardSummaryResponse getSummary() {
        AuthenticatedUser currentUser = TenantContext.currentUser();
        Long clinicId = currentUser.clinicId();
        Long doctorId = currentUser.doctorId();

        List<Appointment> appointments = doctorId != null
                ? appointmentRepository.findAllByClinicIdAndDoctorIdOrderByStartsAtAsc(clinicId, doctorId)
                : appointmentRepository.findAllByClinicIdOrderByStartsAtAsc(clinicId);

        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();

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

        return new DashboardSummaryResponse(todayCount, upcoming, activeDoctorCount);
    }
}
