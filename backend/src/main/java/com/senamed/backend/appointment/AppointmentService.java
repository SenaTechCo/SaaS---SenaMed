package com.senamed.backend.appointment;

import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Authenticated appointments listing for the clinic dashboard (RF-018) - kept separate from
 * {@link PublicSchedulingService}, which is documented as unauthenticated-only.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;

    public AppointmentService(AppointmentRepository appointmentRepository) {
        this.appointmentRepository = appointmentRepository;
    }

    @Transactional(readOnly = true)
    public List<AppointmentResponse> listAll() {
        return appointmentRepository.findAllByClinicIdOrderByStartsAtAsc(TenantContext.currentClinicId()).stream()
                .map(AppointmentResponse::from)
                .toList();
    }

    /** Own-agenda listing for a DOCTOR-role caller (KAN-77). */
    @Transactional(readOnly = true)
    public List<AppointmentResponse> listMine() {
        Long clinicId = TenantContext.currentClinicId();
        Long doctorId = TenantContext.currentDoctorId();
        return appointmentRepository.findAllByClinicIdAndDoctorIdOrderByStartsAtAsc(clinicId, doctorId).stream()
                .map(AppointmentResponse::from)
                .toList();
    }
}
