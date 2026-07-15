-- V7: login proprio para o medico, separado do admin da clinica (Fase 7, KAN-77)

ALTER TABLE users
    ADD COLUMN doctor_id BIGINT REFERENCES doctors (id) ON DELETE CASCADE;

-- Um medico tem no maximo um login. Postgres permite multiplos NULLs numa UNIQUE constraint, entao
-- usuarios ADMIN (doctor_id sempre NULL) nao sao afetados.
ALTER TABLE users
    ADD CONSTRAINT uq_users_doctor_id UNIQUE (doctor_id);
