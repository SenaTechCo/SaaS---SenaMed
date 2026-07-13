package com.senamed.backend.doctor.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.senamed.backend.doctor.DoctorAvailability;

import java.time.LocalTime;

public record AvailabilityResponse(
        Long id,
        Integer dayOfWeek,
        @JsonFormat(pattern = "HH:mm") LocalTime startTime,
        @JsonFormat(pattern = "HH:mm") LocalTime endTime) {

    public static AvailabilityResponse from(DoctorAvailability availability) {
        return new AvailabilityResponse(
                availability.getId(),
                availability.getDayOfWeek(),
                availability.getStartTime(),
                availability.getEndTime());
    }
}
