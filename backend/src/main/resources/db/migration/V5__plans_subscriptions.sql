-- V5: planos comerciais e assinaturas avulsas (Fase 5)
-- RF-019 (planos), RF-020 (checkout avulso por periodo), RF-021/RN-014 (webhook), RF-022/RN-007 (bloqueio)

UPDATE clinics SET status = 'BLOCKED' WHERE status = 'SUSPENDED';
UPDATE clinics SET status = 'CANCELLED' WHERE status = 'CANCELED';
ALTER TABLE clinics ADD CONSTRAINT chk_clinics_status
    CHECK (status IN ('TRIAL','ACTIVE','PAST_DUE','BLOCKED','CANCELLED'));

CREATE TABLE plans (
    id           BIGSERIAL PRIMARY KEY,
    name         VARCHAR(100)   NOT NULL,
    price_amount NUMERIC(10,2)  NOT NULL,
    max_doctors  INT            NOT NULL,
    active       BOOLEAN        NOT NULL DEFAULT true,
    created_at   TIMESTAMP      NOT NULL DEFAULT now(),

    CONSTRAINT chk_plans_price_positive CHECK (price_amount > 0),
    CONSTRAINT chk_plans_max_doctors_positive CHECK (max_doctors > 0)
);

INSERT INTO plans (name, price_amount, max_doctors) VALUES
    ('Básico', 99.90, 3),
    ('Profissional', 199.90, 10),
    ('Ilimitado', 349.90, 999);

CREATE TABLE subscriptions (
    id                    BIGSERIAL PRIMARY KEY,
    clinic_id             BIGINT       NOT NULL REFERENCES clinics (id),
    plan_id               BIGINT       NOT NULL REFERENCES plans (id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    period_months         INT          NOT NULL,
    mp_preference_id      VARCHAR(120),
    mp_payment_id         VARCHAR(120),
    current_period_start  TIMESTAMP,
    current_period_end    TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_subscriptions_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_subscriptions_period_months CHECK (period_months IN (1, 3, 12)),
    CONSTRAINT uq_subscriptions_mp_payment_id UNIQUE (mp_payment_id)
);

CREATE INDEX idx_subscriptions_clinic_id ON subscriptions (clinic_id);

-- Usado pelo scheduler de bloqueio (RF-022/RN-007) para localizar rapidamente a assinatura
-- vigente/atrasada de cada clinica.
CREATE INDEX idx_subscriptions_clinic_id_status_period_end
    ON subscriptions (clinic_id, status, current_period_end);
