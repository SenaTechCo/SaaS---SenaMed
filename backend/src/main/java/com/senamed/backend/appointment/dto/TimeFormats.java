package com.senamed.backend.appointment.dto;

import java.time.format.DateTimeFormatter;

/** Shared "HH:mm" formatting for public scheduling responses. */
final class TimeFormats {

    static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private TimeFormats() {
    }
}
