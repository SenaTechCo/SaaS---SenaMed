package com.senamed.backend.billing;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.billing.dto.CheckoutRequest;
import com.senamed.backend.billing.dto.CheckoutResponse;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PreferenceResult;
import com.senamed.backend.common.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Covers Fase 5 (KAN-66): checkout creation. */
class SubscriptionCheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MercadoPagoClient mercadoPagoClient;

    @Test
    void createCheckout_happyPath_returns201WithPendingSubscription() {
        when(mercadoPagoClient.createPreference(any()))
                .thenReturn(new PreferenceResult("pref-123", "https://mp.example/checkout/pref-123"));

        HttpHeaders headers = registerClinic("Clinica Checkout", "admin@checkout.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Profissional'", Long.class);

        ResponseEntity<CheckoutResponse> response = restTemplate.exchange(
                url("/api/subscriptions/checkout"), HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest(planId, 3), headers), CheckoutResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().checkoutUrl()).isEqualTo("https://mp.example/checkout/pref-123");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, mp_preference_id, period_months FROM subscriptions WHERE id = ?", response.getBody().subscriptionId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("mp_preference_id")).isEqualTo("pref-123");
        assertThat(row.get("period_months")).isEqualTo(3);
    }

    @Test
    void createCheckout_invalidPeriodMonths_returns400() {
        HttpHeaders headers = registerClinic("Clinica Periodo Invalido", "admin@periodoinvalido.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/subscriptions/checkout"), HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest(planId, 2), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createCheckout_unknownPlan_returns404() {
        HttpHeaders headers = registerClinic("Clinica Plano Desconhecido", "admin@planodesconhecido.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/subscriptions/checkout"), HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest(999999L, 1), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
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
