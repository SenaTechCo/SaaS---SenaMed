package com.senamed.backend.notification.whatsapp;

/** Thrown when Meta's own Cloud API is unreachable or returns an error. */
public class WhatsAppIntegrationException extends RuntimeException {

    public WhatsAppIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
