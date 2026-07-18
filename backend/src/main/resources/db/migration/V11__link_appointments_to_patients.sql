-- V11: link appointments to the patient registry (KAN-95 follow-up)

ALTER TABLE appointments
    ADD COLUMN patient_id BIGINT REFERENCES patients (id);

CREATE INDEX idx_appointments_patient_id ON appointments (patient_id);
