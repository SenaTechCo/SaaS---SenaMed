-- V14: widen the appointment status check constraint (V13 widened it for ATTENDED) to also
-- allow NO_SHOW (KAN-101 "Falta").

ALTER TABLE appointments DROP CONSTRAINT chk_appointments_status;
ALTER TABLE appointments ADD CONSTRAINT chk_appointments_status CHECK (status IN ('CONFIRMED', 'ATTENDED', 'NO_SHOW', 'CANCELLED'));
