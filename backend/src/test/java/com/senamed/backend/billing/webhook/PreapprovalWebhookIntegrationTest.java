package com.senamed.backend.billing.webhook;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.billing.dto.CreatePreapprovalRequest;
import com.senamed.backend.billing.dto.PreapprovalCheckoutResponse;
import com.senamed.backend.billing.mercadopago.MercadoPagoClient;
import com.senamed.backend.billing.mercadopago.PaymentResult;
import com.senamed.backend.billing.mercadopago.PreapprovalResult;
import com.senamed.backend.billing.mercadopago.PreapprovalStatusResult;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
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

/** Covers KAN-76: the preapproval webhook branches (status-only + recurring charge) and RN-014 idempotency. */
class PreapprovalWebhookIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private MercadoPagoClient mercadoPagoClient;

    @Test
    void authorizedStatus_updatesLocalStatusOnly_clinicUntouched() {
        when(mercadoPagoClient.createPreapproval(any()))
                .thenReturn(new PreapprovalResult("preap-1", "https://mp.example/preapproval"));

        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval Status", "admin@preapprovalstatus.com", "contato@preapprovalstatus.com");
        Long clinicId = jdbcTemplate.queryForObject("SELECT id FROM clinics WHERE name = ?", Long.class, "Clinica Preapproval Status");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Profissional'", Long.class);
        PreapprovalCheckoutResponse checkout = doCreatePreapproval(headers, planId, 1);

        when(mercadoPagoClient.getPreapproval("preap-1"))
                .thenReturn(new PreapprovalStatusResult("preap-1", "authorized", checkout.preapprovalId().toString()));

        ResponseEntity<Void> response = postWebhook("subscription_preapproval", "preap-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM preapprovals WHERE id = ?", String.class, checkout.preapprovalId()))
                .isEqualTo("AUTHORIZED");
        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("TRIAL");
    }

    @Test
    void approvedCharge_activatesClinicAndSetsCurrentPeriod() {
        when(mercadoPagoClient.createPreapproval(any()))
                .thenReturn(new PreapprovalResult("preap-2", "https://mp.example/preapproval"));

        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval Cobranca", "admin@preapprovalcobranca.com", "contato@preapprovalcobranca.com");
        Long clinicId = jdbcTemplate.queryForObject("SELECT id FROM clinics WHERE name = ?", Long.class, "Clinica Preapproval Cobranca");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Profissional'", Long.class);
        PreapprovalCheckoutResponse checkout = doCreatePreapproval(headers, planId, 1);

        when(mercadoPagoClient.getPayment("pay-charge-1"))
                .thenReturn(new PaymentResult("pay-charge-1", "approved", checkout.preapprovalId().toString()));

        ResponseEntity<Void> response = postWebhook("subscription_authorized_payment", "pay-charge-1");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> preapproval = jdbcTemplate.queryForMap(
                "SELECT status, current_period_end FROM preapprovals WHERE id = ?", checkout.preapprovalId());
        assertThat(preapproval.get("status")).isEqualTo("AUTHORIZED");
        assertThat(preapproval.get("current_period_end")).isNotNull();

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM preapproval_charges WHERE mp_payment_id = ?", Integer.class, "pay-charge-1"))
                .isEqualTo(1);

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("SELECT max_doctors FROM clinics WHERE id = ?", Integer.class, clinicId))
                .isEqualTo(10);
    }

    @Test
    void idempotentReplay_sameApprovedCharge_doesNotReextendPeriod() {
        when(mercadoPagoClient.createPreapproval(any()))
                .thenReturn(new PreapprovalResult("preap-3", "https://mp.example/preapproval"));

        HttpHeaders headers = registerClinicWithEmail("Clinica Preapproval Idempotente", "admin@preapprovalidempotente.com", "contato@preapprovalidempotente.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        PreapprovalCheckoutResponse checkout = doCreatePreapproval(headers, planId, 1);

        when(mercadoPagoClient.getPayment("pay-charge-2"))
                .thenReturn(new PaymentResult("pay-charge-2", "approved", checkout.preapprovalId().toString()));

        postWebhook("subscription_authorized_payment", "pay-charge-2");
        Timestamp firstPeriodEnd = jdbcTemplate.queryForObject(
                "SELECT current_period_end FROM preapprovals WHERE id = ?", Timestamp.class, checkout.preapprovalId());

        postWebhook("subscription_authorized_payment", "pay-charge-2");
        Timestamp secondPeriodEnd = jdbcTemplate.queryForObject(
                "SELECT current_period_end FROM preapprovals WHERE id = ?", Timestamp.class, checkout.preapprovalId());

        assertThat(secondPeriodEnd).isEqualTo(firstPeriodEnd);
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM preapproval_charges WHERE mp_payment_id = ?", Integer.class, "pay-charge-2"))
                .isEqualTo(1);
        verify(mercadoPagoClient, times(2)).getPayment("pay-charge-2");
    }

    @Test
    void unknownExternalReference_returns200_noChangesMade() {
        when(mercadoPagoClient.getPayment("pay-unknown"))
                .thenReturn(new PaymentResult("pay-unknown", "approved", "999999"));

        ResponseEntity<Void> response = postWebhook("subscription_authorized_payment", "pay-unknown");
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private ResponseEntity<Void> postWebhook(String type, String id) {
        MercadoPagoWebhookPayload payload = new MercadoPagoWebhookPayload(type, new MercadoPagoWebhookPayload.Data(id));
        return restTemplate.postForEntity(url("/api/webhooks/mercado-pago"), payload, Void.class);
    }

    private PreapprovalCheckoutResponse doCreatePreapproval(HttpHeaders headers, Long planId, int periodMonths) {
        ResponseEntity<PreapprovalCheckoutResponse> response = restTemplate.exchange(
                url("/api/subscriptions/preapproval"), HttpMethod.POST,
                new HttpEntity<>(new CreatePreapprovalRequest(planId, periodMonths), headers), PreapprovalCheckoutResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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
