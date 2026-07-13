package com.senamed.backend.common;

/**
 * Thrown when a clinic tries to activate/create more doctors than its plan currently allows
 * (RN-015). {@code Clinic.maxDoctors} is a Fase-2 placeholder for the real plan/subscription
 * limits that will be implemented in Fase 5 - regardless of that, this validation must always run
 * server-side, never trusting the frontend to enforce it.
 */
public class DoctorLimitExceededException extends RuntimeException {

    public DoctorLimitExceededException(int maxDoctors) {
        super("Limite de médicos do plano atingido (max: " + maxDoctors + ")");
    }
}
