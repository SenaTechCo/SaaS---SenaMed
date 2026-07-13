-- V1: initial schema for SenaMed Fase 1 (clinics + users)

CREATE TABLE clinics (
    id            BIGSERIAL PRIMARY KEY,
    name          VARCHAR(255) NOT NULL,
    slug          VARCHAR(255) NOT NULL,
    status        VARCHAR(50)  NOT NULL DEFAULT 'TRIAL',
    timezone      VARCHAR(100) NOT NULL DEFAULT 'America/Sao_Paulo',
    description   VARCHAR(1000),
    phone         VARCHAR(50),
    email         VARCHAR(255),
    trial_ends_at TIMESTAMP,
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_clinics_slug UNIQUE (slug)
);

CREATE TABLE users (
    id            BIGSERIAL PRIMARY KEY,
    clinic_id     BIGINT       NOT NULL REFERENCES clinics (id),
    name          VARCHAR(255) NOT NULL,
    email         VARCHAR(255) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(50)  NOT NULL DEFAULT 'ADMIN',
    created_at    TIMESTAMP    NOT NULL DEFAULT now(),
    CONSTRAINT uk_users_email UNIQUE (email)
);

CREATE INDEX idx_users_clinic_id ON users (clinic_id);
