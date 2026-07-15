package com.senamed.backend.doctor;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.DoctorLimitExceededException;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.DoctorUpdateRequest;
import com.senamed.backend.doctor.dto.TimeOffRequest;
import com.senamed.backend.doctor.dto.TimeOffResponse;
import com.senamed.backend.tenant.TenantContext;
import com.senamed.backend.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

/**
 * Doctor CRUD (RF-00x) plus weekly availability windows and time-off periods. {@code Doctor} is
 * the first tenant-scoped entity beyond {@code User} (see {@link Doctor}'s javadoc) - every
 * lookup here is explicitly scoped by {@link TenantContext#currentClinicId()} in addition to the
 * Hibernate filter, so a clinic can never see or mutate another clinic's doctors.
 */
@Service
public class DoctorService {

    private final DoctorRepository doctorRepository;
    private final DoctorAvailabilityRepository availabilityRepository;
    private final DoctorTimeOffRepository timeOffRepository;
    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;

    public DoctorService(
            DoctorRepository doctorRepository,
            DoctorAvailabilityRepository availabilityRepository,
            DoctorTimeOffRepository timeOffRepository,
            ClinicRepository clinicRepository,
            UserRepository userRepository) {
        this.doctorRepository = doctorRepository;
        this.availabilityRepository = availabilityRepository;
        this.timeOffRepository = timeOffRepository;
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
    }

    @Transactional
    public DoctorResponse create(DoctorCreateRequest request) {
        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));

        // RN-015: always enforced server-side, regardless of what the frontend already checked.
        long activeDoctors = doctorRepository.countByClinicIdAndActiveTrue(clinicId);
        if (activeDoctors >= clinic.getMaxDoctors()) {
            throw new DoctorLimitExceededException(clinic.getMaxDoctors());
        }

        Doctor doctor = new Doctor(clinic, request.name(), request.specialty(), request.email(), request.phone());
        doctor = doctorRepository.save(doctor);
        return toResponse(doctor);
    }

    @Transactional(readOnly = true)
    public List<DoctorResponse> listAll() {
        return doctorRepository.findAllByClinicIdOrderByNameAsc(TenantContext.currentClinicId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public DoctorResponse getOne(Long id) {
        return toResponse(loadOwnedDoctor(id));
    }

    @Transactional
    public DoctorResponse update(Long id, DoctorUpdateRequest request) {
        Doctor doctor = loadOwnedDoctor(id);
        doctor.setName(request.name());
        doctor.setSpecialty(request.specialty());
        doctor.setEmail(request.email());
        doctor.setPhone(request.phone());
        return toResponse(doctor);
    }

    /** Soft delete (RF-00x): sets {@code active = false}, the row is never removed. */
    @Transactional
    public void deactivate(Long id) {
        loadOwnedDoctor(id).deactivate();
    }

    /**
     * Replaces ALL of a doctor's weekly availability windows with the given set (delete + insert).
     * This is a deliberate simplification for the MVP - a PUT would be semantically more correct,
     * but the endpoint is a POST per spec.
     */
    @Transactional
    public List<AvailabilityResponse> replaceAvailability(Long doctorId, List<AvailabilityRequest> windows) {
        Doctor doctor = loadOwnedDoctor(doctorId);

        for (AvailabilityRequest window : windows) {
            if (!window.startTime().isBefore(window.endTime())) {
                throw new InvalidRequestException(
                        "startTime must be before endTime (dayOfWeek=" + window.dayOfWeek() + ")");
            }
        }

        availabilityRepository.deleteByDoctorId(doctor.getId());
        availabilityRepository.flush();

        List<DoctorAvailability> entities = windows.stream()
                .map(w -> new DoctorAvailability(doctor, w.dayOfWeek(), w.startTime(), w.endTime()))
                .toList();
        List<DoctorAvailability> saved = availabilityRepository.saveAll(entities);

        return saved.stream()
                .sorted(Comparator.comparing(DoctorAvailability::getDayOfWeek)
                        .thenComparing(DoctorAvailability::getStartTime))
                .map(AvailabilityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listAvailability(Long doctorId) {
        Doctor doctor = loadOwnedDoctor(doctorId);
        return availabilityRepository.findByDoctorIdOrderByDayOfWeekAscStartTimeAsc(doctor.getId()).stream()
                .map(AvailabilityResponse::from)
                .toList();
    }

    /** Time-off periods accumulate over time - this always creates a new record. */
    @Transactional
    public TimeOffResponse createTimeOff(Long doctorId, TimeOffRequest request) {
        Doctor doctor = loadOwnedDoctor(doctorId);

        LocalDate endDate = request.endDate() != null ? request.endDate() : request.startDate();
        if (endDate.isBefore(request.startDate())) {
            throw new InvalidRequestException("endDate cannot be before startDate");
        }

        DoctorTimeOff timeOff = new DoctorTimeOff(doctor, request.startDate(), endDate, request.reason());
        timeOff = timeOffRepository.save(timeOff);
        return TimeOffResponse.from(timeOff);
    }

    @Transactional(readOnly = true)
    public List<TimeOffResponse> listTimeOff(Long doctorId) {
        Doctor doctor = loadOwnedDoctor(doctorId);
        return timeOffRepository.findByDoctorIdOrderByStartDateAsc(doctor.getId()).stream()
                .map(TimeOffResponse::from)
                .toList();
    }

    /**
     * Loads a doctor, explicitly scoped to the current clinic. Returns 404 (via
     * {@link ResourceNotFoundException}) both when the id does not exist and when it belongs to
     * another clinic, so callers cannot distinguish "not found" from "not yours".
     */
    private Doctor loadOwnedDoctor(Long id) {
        return doctorRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }

    private DoctorResponse toResponse(Doctor doctor) {
        return DoctorResponse.from(doctor, userRepository.existsByDoctorId(doctor.getId()));
    }

    /** Own read-only profile for a DOCTOR-role caller (KAN-77). */
    @Transactional(readOnly = true)
    public DoctorResponse getMyProfile() {
        return toResponse(loadOwnedDoctor(TenantContext.currentDoctorId()));
    }

    /** Own read-only weekly availability for a DOCTOR-role caller (KAN-77). */
    @Transactional(readOnly = true)
    public List<AvailabilityResponse> listMyAvailability() {
        return listAvailability(TenantContext.currentDoctorId());
    }

    /** Own read-only time-off periods for a DOCTOR-role caller (KAN-77). */
    @Transactional(readOnly = true)
    public List<TimeOffResponse> listMyTimeOff() {
        return listTimeOff(TenantContext.currentDoctorId());
    }
}
