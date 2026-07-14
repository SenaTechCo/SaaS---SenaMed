-- V4: mensagens de e-mail e confirmacao de presenca (Fase 4)
-- RF-014 (confirmacao imediata), RF-015 (lembrete 24h), RF-016 (confirmacao de presenca),
-- RF-023 (registro de status das mensagens), RN-018 (expiracao de tokens)

ALTER TABLE appointments
    ADD COLUMN confirmed_at TIMESTAMP;

CREATE TABLE appointment_messages (
    id                  BIGSERIAL PRIMARY KEY,
    appointment_id      BIGINT       NOT NULL REFERENCES appointments (id),
    type                VARCHAR(30)  NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    confirmation_token  UUID         NOT NULL DEFAULT gen_random_uuid(),
    token_expires_at    TIMESTAMP,
    scheduled_for       TIMESTAMP    NOT NULL,
    sent_at             TIMESTAMP,
    attempts            INT          NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_appointment_messages_type CHECK (type IN ('CREATED_CONFIRMATION', 'REMINDER_24H')),
    CONSTRAINT chk_appointment_messages_status CHECK (status IN ('PENDING', 'SENT', 'FAILED', 'CANCELLED')),
    CONSTRAINT uq_appointment_messages_confirmation_token UNIQUE (confirmation_token)
);

CREATE INDEX idx_appointment_messages_appointment_id ON appointment_messages (appointment_id);

-- Usado pelo scheduler (RF-023: reenvio de mensagens pendentes) para localizar rapidamente
-- o que esta pronto para envio/reenvio.
CREATE INDEX idx_appointment_messages_status_scheduled_for
    ON appointment_messages (status, scheduled_for);
