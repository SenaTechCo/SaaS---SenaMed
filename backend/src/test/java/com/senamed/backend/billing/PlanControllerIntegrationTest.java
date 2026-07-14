package com.senamed.backend.billing;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.billing.dto.PlanResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers Fase 5 (KAN-65): the seeded plan catalog. */
class PlanControllerIntegrationTest extends AbstractIntegrationTest {

    @Test
    void listPlans_returnsTheThreeSeededPlans() {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest("Clinica Planos", "Admin", "admin@planos.com", "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registerResponse.getBody().token());

        ResponseEntity<List<PlanResponse>> response = restTemplate.exchange(
                url("/api/plans"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<PlanResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(PlanResponse::name)
                .containsExactly("Básico", "Profissional", "Ilimitado");
        assertThat(response.getBody()).extracting(PlanResponse::maxDoctors)
                .containsExactly(3, 10, 999);
    }

    @Test
    void listPlans_withoutToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/plans"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
