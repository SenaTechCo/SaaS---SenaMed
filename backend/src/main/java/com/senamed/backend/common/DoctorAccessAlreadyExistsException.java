package com.senamed.backend.common;

/** Thrown by POST /api/doctors/{id}/access when the doctor already has a linked login. */
public class DoctorAccessAlreadyExistsException extends RuntimeException {

    public DoctorAccessAlreadyExistsException(Long doctorId) {
        super("Doctor " + doctorId + " already has login access granted");
    }
}
