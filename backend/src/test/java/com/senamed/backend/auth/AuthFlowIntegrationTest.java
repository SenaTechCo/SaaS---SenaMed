package com.senamed.backend.auth;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.clinic.dto.ClinicProfileResponse;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
import com.senamed.backend.common.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers RF-001/RF-002/RF-003/RF-004/RF-005 end-to-end (RNF-012): register-clinic + login
 * success, duplicate email conflict, wrong password, and access to the protected
 * /api/clinics/me endpoint with and without a token.
 */
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void registerClinic_thenLogin_thenAccessProtectedRoute_happyPath() {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(
                "Clinica Boa Saude", "Ana Admin", "ana@boasaude.com", "SenhaForte123");

        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);

        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        AuthResponse registerBody = registerResponse.getBody();
        assertThat(registerBody).isNotNull();
        assertThat(registerBody.token()).isNotBlank();
        assertThat(registerBody.clinic().name()).isEqualTo("Clinica Boa Saude");
        assertThat(registerBody.clinic().slug()).isEqualTo("clinica-boa-saude");
        assertThat(registerBody.clinic().status().name()).isEqualTo("TRIAL");
        assertThat(registerBody.clinic().trialEndsAt()).isNotNull();
        assertThat(registerBody.user().email()).isEqualTo("ana@boasaude.com");
        assertThat(registerBody.user().role().name()).isEqualTo("ADMIN");

        // login with the same credentials
        LoginRequest loginRequest = new LoginRequest("ana@boasaude.com", "SenhaForte123");
        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                url("/api/auth/login"), loginRequest, AuthResponse.class);

        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        String token = loginResponse.getBody().token();
        assertThat(token).isNotBlank();

        // access /api/clinics/me WITHOUT token -> 401
        ResponseEntity<ApiError> unauthorized = restTemplate.getForEntity(
                url("/api/clinics/me"), ApiError.class);
        assertThat(unauthorized.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);

        // access /api/clinics/me WITH token -> 200
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        ResponseEntity<ClinicProfileResponse> meResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.GET, new HttpEntity<>(headers), ClinicProfileResponse.class);

        assertThat(meResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(meResponse.getBody()).isNotNull();
        assertThat(meResponse.getBody().slug()).isEqualTo("clinica-boa-saude");
        assertThat(meResponse.getBody().name()).isEqualTo("Clinica Boa Saude");

        // update /api/clinics/me WITH token -> 200 and reflects changes
        ClinicUpdateRequest updateRequest = new ClinicUpdateRequest(
                "Clinica Boa Saude Atualizada", "Nova descricao", "11999998888", "contato@boasaude.com", "America/Sao_Paulo");
        ResponseEntity<ClinicProfileResponse> updateResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.PUT, new HttpEntity<>(updateRequest, headers), ClinicProfileResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Clinica Boa Saude Atualizada");
        assertThat(updateResponse.getBody().description()).isEqualTo("Nova descricao");
        assertThat(updateResponse.getBody().slug()).isEqualTo("clinica-boa-saude"); // unchanged
    }

    @Test
    void registerClinic_withDuplicateEmail_returns409() {
        RegisterClinicRequest first = new RegisterClinicRequest(
                "Clinica Um", "Admin Um", "duplicado@clinica.com", "SenhaForte123");
        restTemplate.postForEntity(url("/api/auth/register-clinic"), first, AuthResponse.class);

        RegisterClinicRequest second = new RegisterClinicRequest(
                "Clinica Dois", "Admin Dois", "duplicado@clinica.com", "OutraSenha123");
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), second, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("duplicado@clinica.com");
    }

    @Test
    void registerClinic_withCollidingName_generatesIncrementalSlug() {
        RegisterClinicRequest first = new RegisterClinicRequest(
                "Clinica Slug Teste", "Admin Um", "slug1@clinica.com", "SenhaForte123");
        ResponseEntity<AuthResponse> firstResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), first, AuthResponse.class);
        assertThat(firstResponse.getBody().clinic().slug()).isEqualTo("clinica-slug-teste");

        RegisterClinicRequest second = new RegisterClinicRequest(
                "Clinica Slug Teste", "Admin Dois", "slug2@clinica.com", "SenhaForte123");
        ResponseEntity<AuthResponse> secondResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), second, AuthResponse.class);
        assertThat(secondResponse.getBody().clinic().slug()).isEqualTo("clinica-slug-teste-2");
    }

    @Test
    void login_withWrongPassword_returns401() {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(
                "Clinica Senha", "Admin Senha", "senha@clinica.com", "SenhaCorreta123");
        restTemplate.postForEntity(url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);

        LoginRequest wrongLogin = new LoginRequest("senha@clinica.com", "SenhaErrada999");
        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/auth/login"), wrongLogin, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void clinicsMe_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/clinics/me"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
}
