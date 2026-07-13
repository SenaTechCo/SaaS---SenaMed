package com.senamed.backend.common;

/**
 * Generic 400 for cross-field/business-rule validation that plain Bean Validation annotations
 * cannot express (e.g. "startTime must be before endTime", "endDate cannot be before startDate").
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }
}
