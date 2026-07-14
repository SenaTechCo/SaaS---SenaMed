package com.senamed.backend.billing;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.billing.dto.CreatePreapprovalRequest;
import com.senamed.backend.billing.dto.PreapprovalCheckoutResponse;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PreapprovalResult;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
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

/** Covers KAN-76: recurring "assinatura recorrente" checkout creation via Mercado Pago Preapproval. */
class PreapprovalCheckoutIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MercadoPagoClient mercadoPagoClient;

    @Test
    void createPreapproval_happyPath_returns201WithPendingPreapproval() {
        when(mercadoPagoClient.createPreapproval(any()))
                .thenReturn(new PreapprovalResult("preap-123", "https://mp.example/preapproval/preap-123"));

        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval", "admin@preapproval.com", "contato@preapproval.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Profissional'", Long.class);

        ResponseEntity<PreapprovalCheckoutResponse> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval"), HttpMethod.POST,
                new HttpEntity<>(new CreatePreapprovalRequest(planId, 1), headers), PreapprovalCheckoutResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().checkoutUrl()).isEqualTo("https://mp.example/preapproval/preap-123");

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, mp_preapproval_id, period_months FROM preapprovals WHERE id = ?", response.getBody().preapprovalId());
        assertThat(row.get("status")).isEqualTo("PENDING");
        assertThat(row.get("mp_preapproval_id")).isEqualTo("preap-123");
        assertThat(row.get("period_months")).isEqualTo(1);
    }

    @Test
    void createPreapproval_invalidPeriodMonths_returns400() {
        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval Periodo Invalido", "admin@preapprovalperiodoinvalido.com", "contato@preapprovalperiodoinvalido.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval"), HttpMethod.POST,
                new HttpEntity<>(new CreatePreapprovalRequest(planId, 2), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createPreapproval_unknownPlan_returns404() {
        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval Plano Desconhecido", "admin@preapprovalplanodesconhecido.com", "contato@preapprovalplanodesconhecido.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval"), HttpMethod.POST,
                new HttpEntity<>(new CreatePreapprovalRequest(999999L, 1), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createPreapproval_clinicWithoutEmail_returns400() {
        HttpHeaders headers = registerClinic("Clinica Sem Email", "admin@sememail.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval"), HttpMethod.POST,
                new HttpEntity<>(new CreatePreapprovalRequest(planId, 1), headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getCurrentPreapproval_none_returns204() {
        HttpHeaders headers = registerClinicWithEmail("Clinica Sem Assinatura Recorrente", "admin@semassinatura.com", "contato@semassinatura.com");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval/me"), HttpMethod.GET, new HttpEntity<>(headers), String.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    private HttpHeaders registerClinicWithEmail(String clinicName, String adminEmail, String clinicEmail) {
        HttpHeaders headers = registerClinic(clinicName, adminEmail);
        ClinicUpdateRequest updateRequest = new ClinicUpdateRequest(
                clinicName, null, null, clinicEmail, "America/Sao_Paulo", null, null, null, null);
        ResponseEntity<String> updateResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.PUT, new HttpEntity<>(updateRequest, headers), String.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        return headers;
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
