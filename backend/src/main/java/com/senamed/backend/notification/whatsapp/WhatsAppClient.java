package com.senamed.backend.notification.whatsapp;

import java.util.List;

/**
 * Thin abstraction over the Meta Cloud API (WhatsApp Business Platform) - kept as an interface
 * (rather than a concrete class directly injected) so tests can {@code @MockitoBean} it and never
 * make a real network call, mirroring {@code MercadoPagoClient}/{@code GoogleCalendarClient}. One
 * app-wide Business Account credential (like Mercado Pago), not per-doctor (unlike Google
 * Calendar) - injected statically in the real implementation rather than passed per call.
 */
public interface WhatsAppClient {

    /**
     * Sends a pre-approved WhatsApp message template. {@code parameters} fill the template's body
     * placeholders in order - their count/order must match whatever template was actually approved
     * in Meta Business Manager for {@code templateName}.
     */
    void sendTemplateMessage(String toPhone, String templateName, List<String> parameters);
}
