package com.senamed.backend.appointment.dto;

import com.senamed.backend.appointment.AppointmentLineItem;

import java.math.BigDecimal;

public record ServiceLineResponse(
        Long id,
        Long serviceId,
        String serviceName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineTotal) {

    public static ServiceLineResponse from(AppointmentLineItem item) {
        return new ServiceLineResponse(
                item.getId(), item.getService().getId(), item.getService().getName(),
                item.getQuantity(), item.getUnitPrice(), item.getLineTotal());
    }
}
