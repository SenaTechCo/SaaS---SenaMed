package com.senamed.backend.billing.mercadopago;

import com.senamed.backend.billing.mercadopago.dto.PaymentApiResponse;
import com.senamed.backend.billing.mercadopago.dto.PreferenceApiResponse;
import com.senamed.backend.billing.mercadopago.dto.PreferenceRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/**
 * Real implementation backed by Spring's {@link RestClient} (available via spring-boot-starter-web,
 * no extra dependency needed). Without a real access-token configured, every call fails with a 401
 * from Mercado Pago's own servers, surfaced here as {@link MercadoPagoIntegrationException} - the
 * same "fails gracefully until real credentials exist" shape as Fase 4's unset SMTP.
 */
@Component
public class MercadoPagoRestClient implements MercadoPagoClient {

    private final RestClient restClient;
    private final String accessToken;

    public MercadoPagoRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${senamed.mercadopago.base-url}") String baseUrl,
            @Value("${senamed.mercadopago.access-token}") String accessToken) {
        this.restClient = restClientBuilder.baseUrl(baseUrl).build();
        this.accessToken = accessToken;
    }

    @Override
    public PreferenceResult createPreference(CreatePreferenceCommand command) {
        PreferenceRequest request = new PreferenceRequest(
                List.of(new PreferenceRequest.Item(command.title(), 1, command.unitPrice(), "BRL")),
                command.externalReference(),
                command.notificationUrl(),
                new PreferenceRequest.BackUrls(command.successUrl(), command.failureUrl(), command.pendingUrl()),
                "approved");

        try {
            PreferenceApiResponse response = restClient.post()
                    .uri("/checkout/preferences")
                    .header("Authorization", "Bearer " + accessToken)
                    .body(request)
                    .retrieve()
                    .body(PreferenceApiResponse.class);

            String checkoutUrl = response.sandboxInitPoint() != null ? response.sandboxInitPoint() : response.initPoint();
            return new PreferenceResult(response.id(), checkoutUrl);
        } catch (RestClientException ex) {
            throw new MercadoPagoIntegrationException("Falha ao criar preferência de pagamento no Mercado Pago", ex);
        }
    }

    @Override
    public PaymentResult getPayment(String paymentId) {
        try {
            PaymentApiResponse response = restClient.get()
                    .uri("/v1/payments/{id}", paymentId)
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(PaymentApiResponse.class);

            return new PaymentResult(paymentId, response.status(), response.externalReference());
        } catch (RestClientException ex) {
            throw new MercadoPagoIntegrationException("Falha ao confirmar pagamento " + paymentId + " no Mercado Pago", ex);
        }
    }
}
