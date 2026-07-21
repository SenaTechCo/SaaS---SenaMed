-- V15: multiple service line items per appointment (KAN-103), replacing the single
-- optional service_id/price snapshot with a sum over line items. The old appointments.service_id
-- column is left in place (unused going forward) rather than dropped, to avoid a destructive
-- migration; appointments.price is still written, now as the sum of line items instead of a
-- single service's price.
CREATE TABLE appointment_line_items (
    id             BIGSERIAL PRIMARY KEY,
    appointment_id BIGINT NOT NULL REFERENCES appointments (id),
    service_id     BIGINT NOT NULL REFERENCES service_offerings (id),
    quantity       INTEGER NOT NULL DEFAULT 1,
    unit_price     NUMERIC(10,2) NOT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT now()
);
CREATE INDEX idx_appointment_line_items_appointment_id ON appointment_line_items (appointment_id);
