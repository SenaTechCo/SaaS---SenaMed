-- V6: assinatura recorrente via Mercado Pago Preapproval (Fase 7, KAN-76)
-- Coexiste com o checkout avulso (subscriptions) - uma clinica usa um OU outro mecanismo.

CREATE TABLE preapprovals (
    id                    BIGSERIAL PRIMARY KEY,
    clinic_id             BIGINT       NOT NULL REFERENCES clinics (id),
    plan_id               BIGINT       NOT NULL REFERENCES plans (id),
    status                VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    period_months         INT          NOT NULL,
    mp_preapproval_id     VARCHAR(120),
    current_period_start  TIMESTAMP,
    current_period_end    TIMESTAMP,
    created_at            TIMESTAMP    NOT NULL DEFAULT now(),
    updated_at            TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT chk_preapprovals_status CHECK (status IN ('PENDING', 'AUTHORIZED', 'PAUSED', 'CANCELLED')),
    CONSTRAINT chk_preapprovals_period_months CHECK (period_months IN (1, 3, 12)),
    CONSTRAINT uq_preapprovals_mp_preapproval_id UNIQUE (mp_preapproval_id)
);

CREATE INDEX idx_preapprovals_clinic_id ON preapprovals (clinic_id);

-- Usado pelo scheduler de bloqueio (mesma finalidade de idx_subscriptions_clinic_id_status_period_end).
CREATE INDEX idx_preapprovals_clinic_id_status_period_end
    ON preapprovals (clinic_id, status, current_period_end);

-- Uma linha por cobranca recorrente confirmada (subscription_authorized_payment), para idempotencia -
-- mesmo papel do UNIQUE(mp_payment_id) em subscriptions, mas por ciclo em vez de por tentativa de checkout.
CREATE TABLE preapproval_charges (
    id             BIGSERIAL PRIMARY KEY,
    preapproval_id BIGINT       NOT NULL REFERENCES preapprovals (id),
    mp_payment_id  VARCHAR(120) NOT NULL,
    status         VARCHAR(20)  NOT NULL,
    charged_at     TIMESTAMP    NOT NULL,
    created_at     TIMESTAMP    NOT NULL DEFAULT now(),

    CONSTRAINT uq_preapproval_charges_mp_payment_id UNIQUE (mp_payment_id)
);

CREATE INDEX idx_preapproval_charges_preapproval_id ON preapproval_charges (preapproval_id);
