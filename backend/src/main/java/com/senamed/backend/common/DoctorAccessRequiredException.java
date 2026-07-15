package com.senamed.backend.common;

/** Thrown when a /api/doctors/me/** endpoint is called by a token with no doctorId (e.g. an ADMIN account). */
public class DoctorAccessRequiredException extends RuntimeException {

    public DoctorAccessRequiredException() {
        super("This endpoint is only available to doctor accounts");
    }
}
