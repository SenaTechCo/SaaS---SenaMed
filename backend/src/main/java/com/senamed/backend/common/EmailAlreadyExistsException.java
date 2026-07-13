package com.senamed.backend.common;

/**
 * Thrown when registering a clinic (or, in the future, inviting a user) with an email that
 * already exists - emails are unique globally across all clinics.
 */
public class EmailAlreadyExistsException extends RuntimeException {

    public EmailAlreadyExistsException(String email) {
        super("An account with email '" + email + "' already exists");
    }
}
