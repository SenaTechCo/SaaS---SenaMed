-- V3: public scheduling (Fase 3) - appointments

CREATE EXTENSION IF NOT EXISTS btree_gist;

CREATE TABLE appointments (
    id               BIGSERIAL PRIMARY KEY,
    clinic_id        BIGINT       NOT NULL REFERENCES clinics (id),
    doctor_id        BIGINT       NOT NULL REFERENCES doctors (id),
    patient_name     VARCHAR(255) NOT NULL,
    patient_email    VARCHAR(255) NOT NULL,
    patient_phone    VARCHAR(50),
    starts_at        TIMESTAMP    NOT NULL,
    ends_at          TIMESTAMP    NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'CONFIRMED',
    lgpd_consent_at  TIMESTAMP    NOT NULL,
    cancel_token     UUID         NOT NULL DEFAULT gen_random_uuid(),
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_appointments_time_range CHECK (starts_at < ends_at),
    CONSTRAINT chk_appointments_status CHECK (status IN ('CONFIRMED', 'CANCELLED')),
    CONSTRAINT uq_appointments_cancel_token UNIQUE (cancel_token)
);

CREATE INDEX idx_appointments_clinic_id ON appointments (clinic_id);
CREATE INDEX idx_appointments_doctor_id ON appointments (doctor_id);

-- Prevencao de conflito de horario no proprio banco (RF-013, RN-005): dois agendamentos
-- CONFIRMED do mesmo medico nao podem ter intervalos [starts_at, ends_at) sobrepostos.
-- Isso e a trava autoritativa contra condicoes de corrida (dois pacientes agendando o
-- mesmo horario simultaneamente); a validacao em Java antes de inserir e so uma
-- verificacao otimista para dar erro amigavel - o banco garante a exclusao de fato.
ALTER TABLE appointments
    ADD CONSTRAINT no_overlapping_confirmed_appointments
    EXCLUDE USING gist (
        doctor_id WITH =,
        tsrange(starts_at, ends_at) WITH &&
    ) WHERE (status = 'CONFIRMED');
