package com.senamed.backend.user;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.user.dto.ChangePasswordRequest;
import com.senamed.backend.user.dto.UpdateProfileRequest;
import com.senamed.backend.user.dto.UserProfileResponse;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers the self-service "Configurações" endpoints under /api/users/me/**. */
class UserSelfServiceIntegrationTest extends AbstractIntegrationTest {

    @Test
    void updateMyProfile_changesNameAndEmail() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Perfil", "admin@perfilupdate.com");

        UpdateProfileRequest request = new UpdateProfileRequest("Admin Renomeado", "novoemail@perfilupdate.com");
        ResponseEntity<UserProfileResponse> response = restTemplate.exchange(
                url("/api/users/me"), HttpMethod.PUT, new HttpEntity<>(request, headers), UserProfileResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().name()).isEqualTo("Admin Renomeado");
        assertThat(response.getBody().email()).isEqualTo("novoemail@perfilupdate.com");

        ResponseEntity<UserProfileResponse> getResponse = restTemplate.exchange(
                url("/api/users/me"), HttpMethod.GET, new HttpEntity<>(headers), UserProfileResponse.class);
        assertThat(getResponse.getBody().email()).isEqualTo("novoemail@perfilupdate.com");
    }

    @Test
    void updateMyProfile_emailAlreadyUsedByAnotherUser_returns409() {
        authHeadersForNewClinic("Clinica Existente", "usado@emailconflito.com");
        HttpHeaders headers = authHeadersForNewClinic("Clinica Conflito", "admin@emailconflito.com");

        UpdateProfileRequest request = new UpdateProfileRequest("Admin", "usado@emailconflito.com");
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/users/me"), HttpMethod.PUT, new HttpEntity<>(request, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void changeMyPassword_wrongCurrentPassword_returns400() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Senha Errada", "admin@senhaerrada.com");

        ChangePasswordRequest request = new ChangePasswordRequest("SenhaErrada123", "NovaSenhaForte123");
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/users/me/password"), HttpMethod.PUT, new HttpEntity<>(request, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void changeMyPassword_correctCurrentPassword_allowsLoginWithNewPassword() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Senha Certa", "admin@senhacerta.com");

        ChangePasswordRequest request = new ChangePasswordRequest("SenhaForte123", "NovaSenhaForte123");
        ResponseEntity<Void> response = restTemplate.exchange(
                url("/api/users/me/password"), HttpMethod.PUT, new HttpEntity<>(request, headers), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("admin@senhacerta.com", "NovaSenhaForte123"), AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<ApiError> oldPasswordLoginResponse = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("admin@senhacerta.com", "SenhaForte123")), ApiError.class);
        assertThat(oldPasswordLoginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private HttpHeaders authHeadersForNewClinic(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registerResponse.getBody().token());
        return headers;
    }
}
