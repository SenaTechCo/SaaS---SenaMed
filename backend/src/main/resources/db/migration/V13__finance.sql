-- V13: appointment billing (service/price) + Contas a Receber (Receivables) and doctor commission
-- configuration (Financeiro) - links attendance to automatic billing.

ALTER TABLE appointments ADD COLUMN service_id BIGINT REFERENCES service_offerings (id);
ALTER TABLE appointments ADD COLUMN price NUMERIC(10,2);

-- V3's check constraint only allowed CONFIRMED/CANCELLED - widen it to include the new ATTENDED
-- status (KAN-100) introduced alongside this migration.
ALTER TABLE appointments DROP CONSTRAINT chk_appointments_status;
ALTER TABLE appointments ADD CONSTRAINT chk_appointments_status CHECK (status IN ('CONFIRMED', 'ATTENDED', 'CANCELLED'));

CREATE TABLE receivables (
    id             BIGSERIAL PRIMARY KEY,
    clinic_id      BIGINT NOT NULL REFERENCES clinics (id),
    appointment_id BIGINT NOT NULL UNIQUE REFERENCES appointments (id),
    doctor_id      BIGINT NOT NULL REFERENCES doctors (id),
    description    VARCHAR(255) NOT NULL,
    amount         NUMERIC(10,2) NOT NULL,
    status         VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    paid_at        TIMESTAMP,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_receivables_clinic_id ON receivables (clinic_id);
CREATE INDEX idx_receivables_doctor_id ON receivables (doctor_id);

CREATE TABLE commission_configs (
    id         BIGSERIAL PRIMARY KEY,
    clinic_id  BIGINT NOT NULL REFERENCES clinics (id),
    doctor_id  BIGINT NOT NULL REFERENCES doctors (id),
    percentage NUMERIC(5,2) NOT NULL,
    active     BOOLEAN NOT NULL DEFAULT TRUE,
    UNIQUE (clinic_id, doctor_id)
);
