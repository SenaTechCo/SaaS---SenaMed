package com.senamed.backend.finance;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorRepository;
import com.senamed.backend.finance.dto.CommissionCalculationResponse;
import com.senamed.backend.finance.dto.CommissionConfigRequest;
import com.senamed.backend.finance.dto.CommissionConfigResponse;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

/**
 * Doctor commission configuration + calculation (Financeiro). Mirrors {@code PatientService}'s
 * tenant-scoping pattern exactly - every lookup here is explicitly scoped by
 * {@link TenantContext#currentClinicId()} in addition to the Hibernate filter.
 */
@Service
public class CommissionService {

    private final CommissionConfigRepository commissionConfigRepository;
    private final ReceivableRepository receivableRepository;
    private final ClinicRepository clinicRepository;
    private final DoctorRepository doctorRepository;

    public CommissionService(
            CommissionConfigRepository commissionConfigRepository,
            ReceivableRepository receivableRepository,
            ClinicRepository clinicRepository,
            DoctorRepository doctorRepository) {
        this.commissionConfigRepository = commissionConfigRepository;
        this.receivableRepository = receivableRepository;
        this.clinicRepository = clinicRepository;
        this.doctorRepository = doctorRepository;
    }

    @Transactional
    public CommissionConfigResponse upsertConfig(Long doctorId, CommissionConfigRequest request) {
        Long clinicId = TenantContext.currentClinicId();
        Doctor doctor = doctorRepository.findByIdAndClinicId(doctorId, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Médico não encontrado"));

        Optional<CommissionConfig> existing = commissionConfigRepository.findByClinicIdAndDoctorId(clinicId, doctorId);
        if (existing.isPresent()) {
            existing.get().setPercentage(request.percentage());
        } else {
            Clinic clinic = clinicRepository.findById(clinicId)
                    .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));
            commissionConfigRepository.save(new CommissionConfig(clinic, doctor, request.percentage()));
        }
        return new CommissionConfigResponse(doctorId, request.percentage(), true);
    }

    @Transactional(readOnly = true)
    public CommissionConfigResponse getConfig(Long doctorId) {
        Long clinicId = TenantContext.currentClinicId();
        return commissionConfigRepository.findByClinicIdAndDoctorId(clinicId, doctorId)
                .map(config -> new CommissionConfigResponse(doctorId, config.getPercentage(), config.isActive()))
                .orElseGet(() -> new CommissionConfigResponse(doctorId, BigDecimal.ZERO, false));
    }

    @Transactional(readOnly = true)
    public CommissionCalculationResponse calculate(Long doctorId, int year, int month) {
        Long clinicId = TenantContext.currentClinicId();
        Doctor doctor = doctorRepository.findByIdAndClinicId(doctorId, clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Médico não encontrado"));

        ZoneId clinicZone = ZoneId.of(doctor.getClinic().getTimezone());
        LocalDate monthStartDate = LocalDate.of(year, month, 1);
        Instant monthStart = monthStartDate.atStartOfDay(clinicZone).toInstant();
        Instant monthEnd = monthStartDate.plusMonths(1).atStartOfDay(clinicZone).toInstant();

        List<Receivable> receivables = receivableRepository
                .findAllByClinicIdAndDoctorIdAndCreatedAtBetween(clinicId, doctorId, monthStart, monthEnd);
        BigDecimal totalBilled = receivables.stream()
                .map(Receivable::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal percentage = getConfig(doctorId).percentage();
        BigDecimal commissionAmount = totalBilled.multiply(percentage).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        return new CommissionCalculationResponse(doctorId, year, month, percentage, totalBilled, commissionAmount);
    }
}
