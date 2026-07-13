package com.senamed.backend.appointment.dto;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

/** GET /api/public/doctors/{doctorId}/available-slots - empty {@code slots} is not an error. */
public record AvailableSlotsResponse(LocalDate date, List<String> slots) {

    public static AvailableSlotsResponse of(LocalDate date, List<LocalTime> slots) {
        return new AvailableSlotsResponse(date, slots.stream().map(TimeFormats.HH_MM::format).toList());
    }
}
