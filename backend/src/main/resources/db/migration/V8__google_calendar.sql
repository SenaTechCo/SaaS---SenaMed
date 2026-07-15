-- V8: sincronizacao com o Google Calendar por medico (Fase 7, KAN-78)

CREATE TABLE doctor_google_calendar_credentials (
    id               BIGSERIAL PRIMARY KEY,
    doctor_id        BIGINT       NOT NULL REFERENCES doctors (id) ON DELETE CASCADE,
    google_email     VARCHAR(255) NOT NULL,
    refresh_token    TEXT         NOT NULL, -- criptografado em repouso via AttributeConverter (AES-GCM)
    connected_at     TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_doctor_google_calendar_credentials_doctor_id UNIQUE (doctor_id)
);

-- Outbox de sincronizacao com o Google Calendar - mesmo papel/formato de appointment_messages
-- (Fase 4), so que para eventos de calendario em vez de e-mails.
CREATE TABLE appointment_calendar_sync_jobs (
    id               BIGSERIAL PRIMARY KEY,
    appointment_id   BIGINT       NOT NULL REFERENCES appointments (id),
    type             VARCHAR(20)  NOT NULL,
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    google_event_id  VARCHAR(255),
    attempts         INT          NOT NULL DEFAULT 0,
    created_at       TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_appointment_calendar_sync_jobs_type CHECK (type IN ('CREATE_EVENT', 'CANCEL_EVENT')),
    CONSTRAINT chk_appointment_calendar_sync_jobs_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'SKIPPED'))
);

CREATE INDEX idx_appointment_calendar_sync_jobs_appointment_id ON appointment_calendar_sync_jobs (appointment_id);
CREATE INDEX idx_appointment_calendar_sync_jobs_status_created_at ON appointment_calendar_sync_jobs (status, created_at);
