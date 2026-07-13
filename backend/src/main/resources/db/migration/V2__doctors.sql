-- V2: doctors, availability and time off (Fase 2) + clinic customization/plan-placeholder fields

-- max_doctors is a simple placeholder for the plan/subscription limit until Fase 5 implements
-- real plans and billing; RN-015 (max doctors per clinic) is enforced against this column in the
-- meantime, always validated server-side (see DoctorService.create).
ALTER TABLE clinics
    ADD COLUMN max_doctors      INTEGER      NOT NULL DEFAULT 3,
    ADD COLUMN logo_url         VARCHAR(500),
    ADD COLUMN cover_image_url  VARCHAR(500),
    ADD COLUMN primary_color    VARCHAR(7),
    ADD COLUMN secondary_color  VARCHAR(7);

CREATE TABLE doctors (
    id          BIGSERIAL PRIMARY KEY,
    clinic_id   BIGINT       NOT NULL REFERENCES clinics (id),
    name        VARCHAR(255) NOT NULL,
    specialty   VARCHAR(255),
    email       VARCHAR(255),
    phone       VARCHAR(50),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT now()
);

CREATE INDEX idx_doctors_clinic_id ON doctors (clinic_id);

CREATE TABLE doctor_availability (
    id          BIGSERIAL PRIMARY KEY,
    doctor_id   BIGINT    NOT NULL REFERENCES doctors (id),
    day_of_week INTEGER   NOT NULL CHECK (day_of_week BETWEEN 1 AND 7), -- 1 = Monday ... 7 = Sunday (ISO-8601)
    start_time  TIME      NOT NULL,
    end_time    TIME      NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    CONSTRAINT chk_doctor_availability_time_range CHECK (start_time < end_time)
);

CREATE INDEX idx_doctor_availability_doctor_id ON doctor_availability (doctor_id);

CREATE TABLE doctor_time_off (
    id         BIGSERIAL PRIMARY KEY,
    doctor_id  BIGINT    NOT NULL REFERENCES doctors (id),
    start_date DATE      NOT NULL,
    end_date   DATE, -- nullable: NULL means "same day as start_date" (API layer always fills it in)
    reason     VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT now()
);

CREATE INDEX idx_doctor_time_off_doctor_id ON doctor_time_off (doctor_id);
