package com.senamed.backend.googlecalendar.dto;

public record GoogleCalendarStatusResponse(boolean connected, String googleEmail) {

    public static GoogleCalendarStatusResponse disconnected() {
        return new GoogleCalendarStatusResponse(false, null);
    }
}
