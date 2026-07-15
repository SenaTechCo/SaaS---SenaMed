package com.senamed.backend.googlecalendar;

/** Thrown when Google's own API/OAuth endpoints are unreachable or return an error. */
public class GoogleCalendarIntegrationException extends RuntimeException {

    public GoogleCalendarIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
