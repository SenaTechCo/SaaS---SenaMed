package com.senamed.backend.tenant;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Covers Fase 5 (RF-022/RN-007): {@link ClinicStatusInterceptor} blocking, with billing endpoints exempted. */
class ClinicStatusInterceptorTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MercadoPagoClient mercadoPagoClient;

    @Test
    void blockedClinic_isRejectedOnOrdinaryEndpoints_butBillingEndpointsStayReachable() {
        when(mercadoPagoClient.createPreference(any()))
                .thenReturn(new PreferenceResult("pref-block", "https://mp.example/checkout"));

        RegisterClinicRequest registerRequest = new RegisterClinicRequest("Clinica Bloqueada", "Admin", "admin@bloqueada.com", "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registerResponse.getBody().token());
        Long clinicId = registerResponse.getBody().clinic().id();

        jdbcTemplate.update("UPDATE clinics SET status = 'BLOCKED' WHERE id = ?", clinicId);

        ResponseEntity<ApiError> doctorsResponse = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.GET, new HttpEntity<>(headers), ApiError.class);
        assertThat(doctorsResponse.getStatusCode().value()).isEqualTo(402);

        ResponseEntity<String> clinicMeResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(clinicMeResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> plansResponse = restTemplate.exchange(
                url("/api/plans"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(plansResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<String> subscriptionsMeResponse = restTemplate.exchange(
                url("/api/subscriptions/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);
        assertThat(subscriptionsMeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        ResponseEntity<CheckoutResponse> checkoutResponse = restTemplate.exchange(
                url("/api/subscriptions/checkout"), HttpMethod.POST,
                new HttpEntity<>(new CheckoutRequest(planId, 1), headers), CheckoutResponse.class);
        assertThat(checkoutResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }
}
