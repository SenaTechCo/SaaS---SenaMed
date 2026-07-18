-- V12: clinic-scoped service/procedure catalog (Catalogo de Servicos) - independent of
-- appointments for now, mirrors the patients table's tenant-scoping pattern.

CREATE TABLE service_offerings (
    id               BIGSERIAL PRIMARY KEY,
    clinic_id        BIGINT NOT NULL REFERENCES clinics (id),
    name             VARCHAR(255) NOT NULL,
    description      VARCHAR(2000),
    duration_minutes INTEGER NOT NULL,
    price            NUMERIC(10,2) NOT NULL,
    active           BOOLEAN NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_service_offerings_clinic_id ON service_offerings (clinic_id);
