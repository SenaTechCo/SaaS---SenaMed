package com.senamed.backend.common;

/** Thrown on login when the email/password combination is invalid. */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("Invalid email or password");
    }
}
