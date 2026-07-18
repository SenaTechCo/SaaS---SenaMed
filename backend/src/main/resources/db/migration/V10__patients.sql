-- V10: patient records (Fase 9) - clinic-scoped patient registry, independent of appointments
-- for now (appointments still carry their own patient_name/patient_email/patient_phone; linking
-- them to this table is left for a future phase).

CREATE TABLE patients (
    id               BIGSERIAL PRIMARY KEY,
    clinic_id        BIGINT       NOT NULL REFERENCES clinics (id),
    name             VARCHAR(255) NOT NULL,
    social_name      VARCHAR(255),
    birth_date       DATE,
    sex              VARCHAR(20),
    cpf              VARCHAR(20),
    email            VARCHAR(255),
    phone            VARCHAR(50),
    zip_code         VARCHAR(20),
    street           VARCHAR(255),
    number           VARCHAR(20),
    complement       VARCHAR(255),
    neighborhood     VARCHAR(255),
    city             VARCHAR(255),
    state            VARCHAR(2),
    referral_source  VARCHAR(255),
    notes            VARCHAR(2000),
    lgpd_consent     BOOLEAN      NOT NULL DEFAULT FALSE,
    lgpd_consent_at  TIMESTAMP,
    active           BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_patients_clinic_id ON patients (clinic_id);
