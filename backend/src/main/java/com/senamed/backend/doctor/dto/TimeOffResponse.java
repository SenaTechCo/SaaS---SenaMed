package com.senamed.backend.doctor.dto;

import com.senamed.backend.doctor.DoctorTimeOff;

import java.time.LocalDate;

public record TimeOffResponse(Long id, LocalDate startDate, LocalDate endDate, String reason) {

    public static TimeOffResponse from(DoctorTimeOff timeOff) {
        return new TimeOffResponse(timeOff.getId(), timeOff.getStartDate(), timeOff.getEndDate(), timeOff.getReason());
    }
}
