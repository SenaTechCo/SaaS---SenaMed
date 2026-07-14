package com.senamed.backend.common;

import com.senamed.backend.clinic.ClinicStatus;

/** Thrown by {@code ClinicStatusInterceptor} when a clinic's status blocks API access (RF-022/RN-007). */
public class ClinicBlockedException extends RuntimeException {

    public ClinicBlockedException(ClinicStatus status) {
        super("Acesso bloqueado: assinatura da clínica está " + status.name());
    }
}
