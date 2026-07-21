package com.senamed.backend.finance.dto;

import com.senamed.backend.finance.Receivable;
import com.senamed.backend.finance.ReceivableStatus;

import java.math.BigDecimal;
import java.time.Instant;

public record ReceivableResponse(
        Long id,
        Long appointmentId,
        String patientName,
        String doctorName,
        String description,
        BigDecimal amount,
        ReceivableStatus status,
        Instant paidAt,
        Instant createdAt) {

    public static ReceivableResponse from(Receivable r) {
        return new ReceivableResponse(
                r.getId(),
                r.getAppointment().getId(),
                r.getAppointment().getPatientName(),
                r.getDoctor().getName(),
                r.getDescription(),
                r.getAmount(),
                r.getStatus(),
                r.getPaidAt(),
                r.getCreatedAt());
    }
}
