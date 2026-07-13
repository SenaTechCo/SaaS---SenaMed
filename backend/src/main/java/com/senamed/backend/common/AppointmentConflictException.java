package com.senamed.backend.common;

/**
 * Thrown when a requested appointment slot is no longer available (RF-013/RN-005: another patient
 * booked it first - either caught by the optimistic Java check or by the database's exclusion
 * constraint on a race), or when a cancellation request cannot be honored (RF-017/RN-004: already
 * cancelled, or inside the 24h cancellation window). Always mapped to HTTP 409.
 */
public class AppointmentConflictException extends RuntimeException {

    public AppointmentConflictException(String message) {
        super(message);
    }
}
