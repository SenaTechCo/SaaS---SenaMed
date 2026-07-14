package com.senamed.backend.billing.webhook;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.billing.dto.CheckoutRequest;
import com.senamed.backend.billing.dto.CheckoutResponse;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PaymentResult;
import com.senamed.backend.billing.mercadopago.PreferenceResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Timestamp;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers Fase 5 (KAN-67): the payment webhook, RN-014 re-confirmation, and idempotency. */
class MercadoPagoWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MercadoPagoClient mercadoPagoClient;

    @Test
    void approvedPayment_activatesClinicAndSubscription() {
        when(mercadoPagoClient.createPreference(any()))
                .thenReturn(new PreferenceResult("pref-1", "https://mp.example/checkout"));

        HttpHeaders headers = registerClinic("Clinica Webhook Aprovado", "admin@webhookaprovado.com");
        Long clinicId = jdbcTemplate.queryForObject("SELECT id FROM clinics WHERE name = ?", Long.class, "Clinica Webhook Aprovado");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Profissional'", Long.class);
        CheckoutResponse checkout = doCheckout(headers, planId, 3);

        when(mercadoPagoClient.getPayment("pay-1"))
                .thenReturn(new PaymentResult("pay-1", "approved", checkout.subscriptionId().toString()));

        ResponseEntity<Void> response = postWebhook("pay-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> subscription = jdbcTemplate.queryForMap(
                "SELECT status, mp_payment_id, current_period_end FROM subscriptions WHERE id = ?", checkout.subscriptionId());
        assertThat(subscription.get("status")).isEqualTo("APPROVED");
        assertThat(subscription.get("mp_payment_id")).isEqualTo("pay-1");
        assertThat(subscription.get("current_period_end")).isNotNull();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT max_doctors FROM clinics WHERE id = ?", Integer.class, clinicId))
                .isEqualTo(10);
    }

    @Test
    void rejectedPayment_marksSubscriptionRejected_clinicUntouched() {
        when(mercadoPagoClient.createPreference(any()))
                .thenReturn(new PreferenceResult("pref-2", "https://mp.example/checkout"));

        HttpHeaders headers = registerClinic("Clinica Webhook Rejeitado", "admin@webhookrejeitado.com");
        Long clinicId = jdbcTemplate.queryForObject("SELECT id FROM clinics WHERE name = ?", Long.class, "Clinica Webhook Rejeitado");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        CheckoutResponse checkout = doCheckout(headers, planId, 1);

        when(mercadoPagoClient.getPayment("pay-2"))
                .thenReturn(new PaymentResult("pay-2", "rejected", checkout.subscriptionId().toString()));

        ResponseEntity<Void> response = postWebhook("pay-2");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM subscriptions WHERE id = ?", String.class, checkout.subscriptionId()))
                .isEqualTo("REJECTED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("TRIAL");
    }

    @Test
    void idempotentReplay_sameApprovedPayment_doesNotReextendPeriod() {
        when(mercadoPagoClient.createPreference(any()))
                .thenReturn(new PreferenceResult("pref-3", "https://mp.example/checkout"));

        HttpHeaders headers = registerClinic("Clinica Webhook Idempotente", "admin@webhookidempotente.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        CheckoutResponse checkout = doCheckout(headers, planId, 1);

        when(mercadoPagoClient.getPayment("pay-3"))
                .thenReturn(new PaymentResult("pay-3", "approved", checkout.subscriptionId().toString()));

        postWebhook("pay-3");
        Timestamp firstPeriodEnd = jdbcTemplate.queryForObject(
                "SELECT current_period_end FROM subscriptions WHERE id = ?", Timestamp.class, checkout.subscriptionId());

        postWebhook("pay-3");
        Timestamp secondPeriodEnd = jdbcTemplate.queryForObject(
                "SELECT current_period_end FROM subscriptions WHERE id = ?", Timestamp.class, checkout.subscriptionId());

        assertThat(secondPeriodEnd).isEqualTo(firstPeriodEnd);
        verify(mercadoPagoClient, times(2)).getPayment("pay-3");
    }

    @Test
    void unknownExternalReference_returns200_noChangesMade() {
        when(mercadoPagoClient.getPayment("pay-unknown"))
                .thenReturn(new PaymentResult("pay-unknown", "approved", "999999"));

        ResponseEntity<Void> response = postWebhook("pay-unknown");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Void> postWebhook(String paymentId) {
        MercadoPagoWebhookPayload payload = new MercadoPagoWebhookPayload("payment", new MercadoPagoWebhookPayload.Data(paymentId));
        return restTemplate.postForEntity(url("/api/webhooks/mercado-pago"), payload, Void.class);
    }

    private CheckoutResponse doCheckout(HttpHeaders headers, Long planId, int periodMonths) {
        ResponseEntity<CheckoutResponse> response = restTemplate.exchange(
                url("/api/subscriptions/checkout"), HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest(planId, periodMonths), headers), CheckoutResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpHeaders registerClinic(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registerResponse.getBody().token());
        return headers;
    }
}
