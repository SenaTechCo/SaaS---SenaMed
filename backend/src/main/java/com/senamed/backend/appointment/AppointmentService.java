package com.senamed.backend.appointment;

import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentRescheduleRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.event.AppointmentAttendedEvent;
import com.senamed.backend.appointment.event.AppointmentCancelledEvent;
import com.senamed.backend.appointment.event.AppointmentCreatedEvent;
import com.senamed.backend.catalog.ServiceOffering;
import com.senamed.backend.catalog.ServiceOfferingRepository;
import com.senamed.backend.common.AppointmentConflictException;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.patient.Patient;
import com.senamed.backend.patient.PatientRepository;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Authenticated appointments listing and staff-initiated create/cancel/reschedule for the clinic
 * dashboard (RF-018, KAN-93) - kept separate from {@link PublicSchedulingService}, which is
 * documented as unauthenticated-only.
 */
@Service
public class AppointmentService {

    private final AppointmentRepository appointmentRepository;
    private final DoctorRepository doctorRepository;
    private final PatientRepository patientRepository;
    private final ServiceOfferingRepository serviceOfferingRepository;
    private final ApplicationEventPublisher eventPublisher;

    public AppointmentService(
            AppointmentRepository appointmentRepository,
            DoctorRepository doctorRepository,
            PatientRepository patientRepository,
            ServiceOfferingRepository serviceOfferingRepository,
            ApplicationEventPublisher eventPublisher) {
        this.appointmentRepository = appointmentRepository;
        this.doctorRepository = doctorRepository;
        this.patientRepository = patientRepository;
        this.serviceOfferingRepository = serviceOfferingRepository;
        this.eventPublisher = eventPublisher;
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

    /**
     * Staff (ADMIN) booking a patient in manually (by phone/at the counter) - KAN-95. Reuses the
     * same LGPD-consent business rule and double-booking conflict handling as
     * {@link PublicSchedulingService#create}, but skips the doctor's weekly-availability window
     * check: staff are trusted to know when a doctor can actually see a patient, and requiring a
     * configured availability window here would block legitimate exception bookings.
     */
    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        if (!Boolean.TRUE.equals(request.lgpdConsent())) {
            throw new InvalidRequestException(
                    "É necessário confirmar o consentimento de tratamento de dados (LGPD) do paciente.");
        }

        Long clinicId = TenantContext.currentClinicId();
        Doctor doctor = doctorRepository.findByIdAndActiveTrue(request.doctorId())
                .filter(candidate -> candidate.getClinic().getId().equals(clinicId))
                .orElseThrow(() -> new ResourceNotFoundException("Médico não encontrado"));

        ZoneId clinicZone = ZoneId.of(doctor.getClinic().getTimezone());
        LocalDateTime startsAt = LocalDateTime.of(request.date(), request.startTime());
        if (startsAt.isBefore(LocalDateTime.now(clinicZone))) {
            throw new InvalidRequestException("Não é possível agendar em uma data/horário no passado.");
        }
        LocalDateTime endsAt = startsAt.plusMinutes(PublicSchedulingService.SLOT_DURATION_MINUTES);

        Patient patient = null;
        if (request.patientId() != null) {
            patient = patientRepository.findByIdAndClinicId(request.patientId(), clinicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Paciente não encontrado"));
        }

        ServiceOffering service = null;
        if (request.serviceId() != null) {
            service = serviceOfferingRepository.findByIdAndClinicId(request.serviceId(), clinicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Serviço não encontrado"));
        }

        Appointment appointment = new Appointment(
                doctor, patient, service, request.patientName(), request.patientEmail(), request.patientPhone(),
                startsAt, endsAt, Instant.now());
        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            // Same exclusion-constraint/lock-contention race covered by PublicSchedulingService#create.
            throw new AppointmentConflictException(
                    "Já existe um agendamento marcado para este médico neste horário.");
        }

        eventPublisher.publishEvent(new AppointmentCreatedEvent(appointment.getId()));
        return AppointmentResponse.from(appointment);
    }

    /** Staff cancelling a patient's appointment from the dashboard (KAN-97), not via the patient's token link. */
    @Transactional
    public AppointmentResponse cancel(Long id) {
        Appointment appointment = loadOwn(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new AppointmentConflictException("Este agendamento já foi cancelado.");
        }
        appointment.cancel();
        eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId()));
        return AppointmentResponse.from(appointment);
    }

    /** Staff moving a patient's appointment to a different date/time (KAN-97), same doctor. */
    @Transactional
    public AppointmentResponse reschedule(Long id, AppointmentRescheduleRequest request) {
        Appointment appointment = loadOwn(id);
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new AppointmentConflictException("Não é possível reagendar um agendamento cancelado.");
        }

        ZoneId clinicZone = ZoneId.of(appointment.getClinic().getTimezone());
        LocalDateTime startsAt = LocalDateTime.of(request.date(), request.startTime());
        if (startsAt.isBefore(LocalDateTime.now(clinicZone))) {
            throw new InvalidRequestException("Não é possível reagendar para uma data/horário no passado.");
        }
        LocalDateTime endsAt = startsAt.plusMinutes(PublicSchedulingService.SLOT_DURATION_MINUTES);

        appointment.reschedule(startsAt, endsAt);
        try {
            appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            throw new AppointmentConflictException(
                    "Já existe um agendamento marcado para este médico neste horário.");
        }
        return AppointmentResponse.from(appointment);
    }

    /** Staff marking a patient's appointment as attended (KAN-100) - triggers auto-billing when a service was selected. */
    @Transactional
    public AppointmentResponse markAttended(Long id) {
        Appointment appointment = loadOwn(id);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new AppointmentConflictException("Só é possível marcar como atendido um agendamento confirmado.");
        }
        appointment.markAttended();
        eventPublisher.publishEvent(new AppointmentAttendedEvent(appointment.getId()));
        return AppointmentResponse.from(appointment);
    }

    /** Staff marking a patient's appointment as a no-show (KAN-101) - no financial side effect, unlike {@link #markAttended}. */
    @Transactional
    public AppointmentResponse markNoShow(Long id) {
        Appointment appointment = loadOwn(id);
        if (appointment.getStatus() != AppointmentStatus.CONFIRMED) {
            throw new AppointmentConflictException("Só é possível marcar falta em um agendamento confirmado.");
        }
        appointment.markNoShow();
        return AppointmentResponse.from(appointment);
    }

    private Appointment loadOwn(Long id) {
        Long clinicId = TenantContext.currentClinicId();
        Appointment appointment = appointmentRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        if (!appointment.getClinic().getId().equals(clinicId)) {
            throw new ResourceNotFoundException("Agendamento não encontrado");
        }
        return appointment;
    }
}
