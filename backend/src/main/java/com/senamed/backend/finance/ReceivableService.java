package com.senamed.backend.finance;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.finance.dto.FinanceSummaryResponse;
import com.senamed.backend.finance.dto.ReceivableResponse;
import com.senamed.backend.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Contas a Receber (Financeiro) CRUD/listing. Mirrors {@code PatientService}'s tenant-scoping
 * pattern exactly - every lookup here is explicitly scoped by {@link TenantContext#currentClinicId()}
 * in addition to the Hibernate filter. Clinics are small enough at this stage that summing amounts
 * in Java (rather than an aggregate query) is not a performance concern - see
 * {@code DashboardService}'s javadoc for the same precedent.
 */
@Service
public class ReceivableService {

    private final ReceivableRepository receivableRepository;
    private final ClinicRepository clinicRepository;

    public ReceivableService(ReceivableRepository receivableRepository, ClinicRepository clinicRepository) {
        this.receivableRepository = receivableRepository;
        this.clinicRepository = clinicRepository;
    }

    @Transactional(readOnly = true)
    public List<ReceivableResponse> listAll(ReceivableStatus status) {
        Long clinicId = TenantContext.currentClinicId();
        List<Receivable> receivables = status != null
                ? receivableRepository.findAllByClinicIdAndStatusOrderByCreatedAtDesc(clinicId, status)
                : receivableRepository.findAllByClinicIdOrderByCreatedAtDesc(clinicId);
        return receivables.stream().map(ReceivableResponse::from).toList();
    }

    @Transactional
    public ReceivableResponse markPaid(Long id) {
        Receivable receivable = receivableRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Conta a receber não encontrada"));
        if (receivable.getStatus() != ReceivableStatus.PENDING) {
            throw new InvalidRequestException("Esta conta já foi paga.");
        }
        receivable.markPaid();
        return ReceivableResponse.from(receivable);
    }

    @Transactional(readOnly = true)
    public FinanceSummaryResponse summary() {
        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));
        ZoneId clinicZone = ZoneId.of(clinic.getTimezone());

        BigDecimal pendingTotal = receivableRepository
                .findAllByClinicIdAndStatusOrderByCreatedAtDesc(clinicId, ReceivableStatus.PENDING).stream()
                .map(Receivable::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate today = LocalDate.now(clinicZone);
        Instant monthStart = today.withDayOfMonth(1).atStartOfDay(clinicZone).toInstant();
        Instant monthEnd = today.withDayOfMonth(1).plusMonths(1).atStartOfDay(clinicZone).toInstant();

        BigDecimal paidThisMonthTotal = receivableRepository
                .findAllByClinicIdAndStatusOrderByCreatedAtDesc(clinicId, ReceivableStatus.PAID).stream()
                .filter(r -> r.getPaidAt() != null && !r.getPaidAt().isBefore(monthStart) && r.getPaidAt().isBefore(monthEnd))
                .map(Receivable::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new FinanceSummaryResponse(pendingTotal, paidThisMonthTotal);
    }
}
