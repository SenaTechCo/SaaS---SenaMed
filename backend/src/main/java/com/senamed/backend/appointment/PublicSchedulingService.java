package com.senamed.backend.appointment;

import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.dto.AvailableSlotsResponse;
import com.senamed.backend.appointment.dto.PublicClinicResponse;
import com.senamed.backend.appointment.event.AppointmentCancelledEvent;
import com.senamed.backend.appointment.event.AppointmentCreatedEvent;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.AppointmentConflictException;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorAvailability;
import com.senamed.backend.doctor.DoctorAvailabilityRepository;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.doctor.DoctorTimeOff;
import com.senamed.backend.doctor.DoctorTimeOffRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.ConcurrencyFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Backs the public, unauthenticated scheduling flow (Fase 3, KAN-53..KAN-59): browsing a clinic's
 * page, listing a doctor's free slots, booking, and cancelling an appointment.
 *
 * <p><strong>Every query here is explicitly scoped</strong> by a {@code clinicId} (resolved from
 * the clinic's slug) or by an already-validated {@code doctorId} - see {@link Appointment}'s
 * javadoc for why the Hibernate tenant filter cannot be relied on for these requests.</p>
 */
@Service
public class PublicSchedulingService {

    /**
     * Fixed appointment slot length. There is no per-doctor/per-clinic configurable duration yet
     * in the data model - this is a placeholder until a future phase adds that.
     */
    static final int SLOT_DURATION_MINUTES = 30;

    /** RF-017/RN-004: cancellation is blocked once fewer than this many hours remain. */
    private static final int MIN_HOURS_BEFORE_CANCEL = 24;

    private final ClinicRepository clinicRepository;
    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorTimeOffRepository timeOffRepository;
    private final AppointmentRepository appointmentRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PublicSchedulingService(
            ClinicRepository clinicRepository,
            DoctorRepository doctorRepository,
            DoctorAvailabilityRepository availabilityRepository,
            DoctorTimeOffRepository timeOffRepository,
            AppointmentRepository appointmentRepository,
            ApplicationEventPublisher eventPublisher) {
        this.clinicRepository = clinicRepository;
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.timeOffRepository = timeOffRepository;
        this.appointmentRepository = appointmentRepository;
        this.eventPublisher = eventPublisher;
    }

    @Transactional(readOnly = true)
    public PublicClinicResponse getClinicBySlug(String slug) {
        Clinic clinic = clinicRepository.findBySlug(slug)
                .orElseThrow(() -> new ResourceNotFoundException("Clínica não encontrada"));
        List<Doctor> activeDoctors = doctorRepository.findAllByClinicIdAndActiveTrueOrderByNameAsc(clinic.getId());
        return PublicClinicResponse.from(clinic, activeDoctors);
    }

    @Transactional(readOnly = true)
    public AvailableSlotsResponse getAvailableSlots(Long doctorId, String rawDate) {
        LocalDate date = parseDate(rawDate);
        Doctor doctor = loadActiveDoctor(doctorId);
        ZoneId clinicZone = ZoneId.of(doctor.getClinic().getTimezone());
        if (date.isBefore(LocalDate.now(clinicZone))) {
            throw new InvalidRequestException("date não pode estar no passado");
        }

        return AvailableSlotsResponse.of(date, computeAvailableSlots(doctor, date));
    }

    @Transactional
    public AppointmentResponse create(AppointmentCreateRequest request) {
        if (!Boolean.TRUE.equals(request.lgpdConsent())) {
            throw new InvalidRequestException(
                    "É necessário confirmar o consentimento de tratamento de dados (LGPD) para concluir o agendamento.");
        }

        Doctor doctor = loadActiveDoctor(request.doctorId());

        ZoneId clinicZone = ZoneId.of(doctor.getClinic().getTimezone());
        LocalDateTime startsAt = LocalDateTime.of(request.date(), request.startTime());
        if (startsAt.isBefore(LocalDateTime.now(clinicZone))) {
            throw new InvalidRequestException("Não é possível agendar em uma data/horário no passado.");
        }
        LocalDateTime endsAt = startsAt.plusMinutes(SLOT_DURATION_MINUTES);

        // Optimistic, friendly-message check: revalidate against the server's own availability
        // computation (never trust that the slot the client showed is still free - another
        // patient may have booked it between listing and submitting).
        if (!computeAvailableSlots(doctor, request.date()).contains(request.startTime())) {
            throw new AppointmentConflictException(
                    "Este horário não está mais disponível. Por favor, escolha outro horário.");
        }

        Appointment appointment = new Appointment(
                doctor, request.patientName(), request.patientEmail(), request.patientPhone(),
                startsAt, endsAt, Instant.now());
        try {
            appointment = appointmentRepository.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException | ConcurrencyFailureException ex) {
            // The database's exclusion constraint (see V3 migration) is the real, race-proof
            // guarantee against double-booking; this only catches the rare case where two
            // requests for the exact same slot both pass the check above concurrently. Under high
            // concurrency Postgres can report this either as a clean exclusion-constraint violation
            // (DataIntegrityViolationException) or, since checking a GiST exclusion constraint takes
            // its own locks, as a deadlock between two competing inserts
            // (ConcurrencyFailureException/CannotAcquireLockException) - both mean the same thing
            // from the caller's perspective: someone else won the race for this slot.
            throw new AppointmentConflictException(
                    "Este horário acabou de ser reservado por outro paciente. Por favor, escolha outro horário.");
        }

        eventPublisher.publishEvent(new AppointmentCreatedEvent(appointment.getId()));
        return AppointmentResponse.from(appointment);
    }

    @Transactional
    public AppointmentResponse cancel(String rawCancelToken) {
        UUID cancelToken;
        try {
            cancelToken = UUID.fromString(rawCancelToken);
        } catch (IllegalArgumentException ex) {
            // A malformed token is indistinguishable from an unknown one to the caller - both 404.
            throw new ResourceNotFoundException("Agendamento não encontrado");
        }

        Appointment appointment = appointmentRepository.findByCancelToken(cancelToken)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new AppointmentConflictException("Este agendamento já foi cancelado.");
        }
        ZoneId clinicZone = ZoneId.of(appointment.getClinic().getTimezone());
        if (appointment.getStartsAt().isBefore(LocalDateTime.now(clinicZone).plusHours(MIN_HOURS_BEFORE_CANCEL))) {
            throw new AppointmentConflictException(
                    "Cancelamento permitido apenas até 24h antes do horário agendado.");
        }

        appointment.cancel();
        eventPublisher.publishEvent(new AppointmentCancelledEvent(appointment.getId()));
        return AppointmentResponse.from(appointment);
    }

    private Doctor loadActiveDoctor(Long doctorId) {
        return doctorRepository.findByIdAndActiveTrue(doctorId)
                .orElseThrow(() -> new ResourceNotFoundException("Médico não encontrado"));
    }

    private LocalDate parseDate(String rawDate) {
        try {
            return LocalDate.parse(rawDate);
        } catch (DateTimeParseException | NullPointerException ex) {
            throw new InvalidRequestException("date deve estar no formato YYYY-MM-DD");
        }
    }

    /**
     * Combines weekly availability windows, time-off periods and already-booked appointments to
     * compute the free 30-minute slots for a single day (RF-013). Minutes-of-day arithmetic is
     * used (instead of {@link LocalTime#plusMinutes}) to avoid silently wrapping past midnight if
     * a window ever ends very close to it.
     */
    private List<LocalTime> computeAvailableSlots(Doctor doctor, LocalDate date) {
        int isoDayOfWeek = date.getDayOfWeek().getValue();

        boolean coveredByTimeOff = timeOffRepository.findByDoctorIdOrderByStartDateAsc(doctor.getId()).stream()
                .anyMatch(timeOff -> coversDate(timeOff, date));
        if (coveredByTimeOff) {
            return List.of();
        }

        List<DoctorAvailability> windows = availabilityRepository
                .findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId()).stream()
                .filter(window -> window.getDayOfWeek() == isoDayOfWeek)
                .toList();
        if (windows.isEmpty()) {
            return List.of();
        }

        LocalDateTime dayStart = date.atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        List<Appointment> confirmedAppointments = appointmentRepository
                .findByDoctorIdAndStatusAndStartsAtGreaterThanEqualAndStartsAtLessThan(
                        doctor.getId(), AppointmentStatus.CONFIRMED, dayStart, dayEnd);

        List<LocalTime> slots = new ArrayList<>();
        for (DoctorAvailability window : windows) {
            int windowStartMinutes = window.getStartTime().toSecondOfDay() / 60;
            int windowEndMinutes = window.getEndTime().toSecondOfDay() / 60;

            for (int slotStart = windowStartMinutes;
                    slotStart + SLOT_DURATION_MINUTES <= windowEndMinutes;
                    slotStart += SLOT_DURATION_MINUTES) {
                LocalTime candidate = LocalTime.ofSecondOfDay(slotStart * 60L);
                LocalDateTime candidateStart = LocalDateTime.of(date, candidate);
                LocalDateTime candidateEnd = candidateStart.plusMinutes(SLOT_DURATION_MINUTES);

                boolean occupied = confirmedAppointments.stream()
                        .anyMatch(a -> a.getStartsAt().isBefore(candidateEnd) && a.getEndsAt().isAfter(candidateStart));
                if (!occupied) {
                    slots.add(candidate);
                }
            }
        }
        return slots;
    }

    private boolean coversDate(DoctorTimeOff timeOff, LocalDate date) {
        LocalDate end = timeOff.getEndDate() != null ? timeOff.getEndDate() : timeOff.getStartDate();
        return !date.isBefore(timeOff.getStartDate()) && !date.isAfter(end);
    }
}
