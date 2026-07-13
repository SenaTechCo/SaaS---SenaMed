package com.senamed.backend.common;

/** Thrown when a tenant-scoped resource cannot be found for the current clinic. */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }
}
