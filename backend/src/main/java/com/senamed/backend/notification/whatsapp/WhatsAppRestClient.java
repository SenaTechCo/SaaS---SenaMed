package com.senamed.backend.notification.whatsapp;

import com.senamed.backend.notification.whatsapp.dto.WhatsAppMessageRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Real implementation backed by Spring's {@link RestClient} (mirrors {@code MercadoPagoRestClient}
 * / {@code GoogleCalendarRestClient}). Without a real Meta Business Account/app configured, every
 * call fails - surfaced here as {@link WhatsAppIntegrationException}, the same "fails gracefully
 * until real credentials exist" shape used throughout this codebase.
 */
@Component
public class WhatsAppRestClient implements WhatsAppClient {

    private final RestClient restClient;
    private final String accessToken;
    private final String phoneNumberId;
    private final String languageCode;

    public WhatsAppRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${senamed.whatsapp.base-url}") String baseUrl,
            @Value("${senamed.whatsapp.access-token}") String accessToken,
            @Value("${senamed.whatsapp.phone-number-id}") String phoneNumberId,
            @Value("${senamed.whatsapp.language-code}") String languageCode) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.accessToken = accessToken;
        this.phoneNumberId = phoneNumberId;
        this.languageCode = languageCode;
    }

    @Override
    public void sendTemplateMessage(String toPhone, String templateName, List<String> parameters) {
        WhatsAppMessageRequest request = new WhatsAppMessageRequest(
                "whatsapp",
                normalizePhone(toPhone),
                "template",
                new WhatsAppMessageRequest.Template(
                        templateName,
                        new WhatsAppMessageRequest.Template.Language(languageCode),
                        List.of(new WhatsAppMessageRequest.Template.Component(
                                "body",
                                parameters.stream().map(WhatsAppMessageRequest.Template.Component.Parameter::text).toList()))));

        try {
            restClient.post()
                    .uri("/{phoneNumberId}/messages", phoneNumberId)
                    .header("Authorization", "Bearer " + accessToken)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException ex) {
            throw new WhatsAppIntegrationException("Falha ao enviar mensagem via WhatsApp", ex);
        }
    }

    /**
     * The Meta Cloud API expects the destination number with country code, no leading {@code +}.
     * Assumes Brazilian numbers (country code 55) when none is present - a simplification worth
     * revisiting once real phone numbers are exercised against a real Business Account.
     */
    private static String normalizePhone(String rawPhone) {
        String digits = rawPhone.replaceAll("\\D", "");
        return digits.startsWith("55") ? digits : "55" + digits;
    }
}
