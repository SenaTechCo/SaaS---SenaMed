-- V9: segundo canal de notificacao (WhatsApp, via Meta Cloud API) para as mesmas mensagens
-- ja enviadas por e-mail (Fase 4, KAN-79)

ALTER TABLE appointment_messages
    ADD COLUMN channel VARCHAR(20) NOT NULL DEFAULT 'EMAIL';

ALTER TABLE appointment_messages
    ADD CONSTRAINT chk_appointment_messages_channel CHECK (channel IN ('EMAIL', 'WHATSAPP'));
