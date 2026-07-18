package com.senamed.backend.patient;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.patient.dto.PatientCreateRequest;
import com.senamed.backend.patient.dto.PatientResponse;
import com.senamed.backend.patient.dto.PatientUpdateRequest;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * Patient CRUD (Fase 9). Mirrors {@code DoctorService}'s tenant-scoping pattern exactly - every
 * lookup here is explicitly scoped by {@link TenantContext#currentClinicId()} in addition to the
 * Hibernate filter, so a clinic can never see or mutate another clinic's patients.
 */
@Service
public class PatientService {

    private final PatientRepository patientRepository;
    private final ClinicRepository clinicRepository;

    public PatientService(PatientRepository patientRepository, ClinicRepository clinicRepository) {
        this.patientRepository = patientRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional
    public PatientResponse create(PatientCreateRequest request) {
        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));

        Patient patient = new Patient(clinic, request.name());
        patient.setSocialName(request.socialName());
        patient.setBirthDate(request.birthDate());
        patient.setSex(request.sex());
        patient.setCpf(request.cpf());
        patient.setEmail(request.email());
        patient.setPhone(request.phone());
        patient.setZipCode(request.zipCode());
        patient.setStreet(request.street());
        patient.setNumber(request.number());
        patient.setComplement(request.complement());
        patient.setNeighborhood(request.neighborhood());
        patient.setCity(request.city());
        patient.setState(request.state());
        patient.setReferralSource(request.referralSource());
        patient.setNotes(request.notes());
        patient.setLgpdConsent(request.lgpdConsent());

        patient = patientRepository.save(patient);
        return PatientResponse.from(patient);
    }

    @Transactional(readOnly = true)
    public List<PatientResponse> listAll(String search) {
        Long clinicId = TenantContext.currentClinicId();
        List<Patient> patients = StringUtils.hasText(search)
                ? patientRepository.findAllByClinicIdAndNameContainingIgnoreCaseOrderByNameAsc(clinicId, search.trim())
                : patientRepository.findAllByClinicIdOrderByNameAsc(clinicId);
        return patients.stream().map(PatientResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public PatientResponse getOne(Long id) {
        return PatientResponse.from(loadOwnedPatient(id));
    }

    @Transactional
    public PatientResponse update(Long id, PatientUpdateRequest request) {
        Patient patient = loadOwnedPatient(id);
        patient.setName(request.name());
        patient.setSocialName(request.socialName());
        patient.setBirthDate(request.birthDate());
        patient.setSex(request.sex());
        patient.setCpf(request.cpf());
        patient.setEmail(request.email());
        patient.setPhone(request.phone());
        patient.setZipCode(request.zipCode());
        patient.setStreet(request.street());
        patient.setNumber(request.number());
        patient.setComplement(request.complement());
        patient.setNeighborhood(request.neighborhood());
        patient.setCity(request.city());
        patient.setState(request.state());
        patient.setReferralSource(request.referralSource());
        patient.setNotes(request.notes());
        patient.setLgpdConsent(request.lgpdConsent());
        return PatientResponse.from(patient);
    }

    /** Soft delete: sets {@code active = false}, the row is never removed. */
    @Transactional
    public void deactivate(Long id) {
        loadOwnedPatient(id).deactivate();
    }

    @Transactional
    public PatientResponse restore(Long id) {
        Patient patient = loadOwnedPatient(id);
        patient.restore();
        return PatientResponse.from(patient);
    }

    /**
     * Loads a patient, explicitly scoped to the current clinic. Returns 404 (via
     * {@link ResourceNotFoundException}) both when the id does not exist and when it belongs to
     * another clinic, so callers cannot distinguish "not found" from "not yours".
     */
    private Patient loadOwnedPatient(Long id) {
        return patientRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Patient not found: " + id));
    }
}
