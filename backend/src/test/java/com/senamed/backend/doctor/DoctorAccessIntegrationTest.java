package com.senamed.backend.doctor;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers KAN-77: granting/revoking a doctor's own login. */
class DoctorAccessIntegrationTest extends AbstractIntegrationTest {

    @Test
    void grantAccess_happyPath_returns201AndDoctorCanLogin() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Acesso Medico", "admin@acessomedico.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Joao Silva", "Cardiologia");

        ResponseEntity<DoctorAccessResponse> grantResponse = grantAccess(
                adminHeaders, doctor.id(), "joao.silva@acessomedico.com", "SenhaForte123");

        assertThat(grantResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(grantResponse.getBody().doctorId()).isEqualTo(doctor.id());
        assertThat(grantResponse.getBody().email()).isEqualTo("joao.silva@acessomedico.com");

        ResponseEntity<AuthResponse> loginResponse = restTemplate.postForEntity(
                url("/api/auth/login"),
                new LoginRequest("joao.silva@acessomedico.com", "SenhaForte123"),
                AuthResponse.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(loginResponse.getBody().user().role().name()).isEqualTo("DOCTOR");
        assertThat(loginResponse.getBody().user().doctorId()).isEqualTo(doctor.id());
    }

    @Test
    void grantAccess_duplicateGrant_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Acesso Duplicado", "admin@acessoduplicado.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Maria Souza", "Pediatria");

        grantAccess(adminHeaders, doctor.id(), "maria1@acessoduplicado.com", "SenhaForte123");
        ResponseEntity<ApiError> secondGrant = restTemplate.exchange(
                url("/api/doctors/" + doctor.id() + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest("maria2@acessoduplicado.com", "SenhaForte123"), adminHeaders),
                ApiError.class);

        assertThat(secondGrant.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondGrant.getBody().message()).contains(doctor.id().toString());
    }

    @Test
    void grantAccess_emailAlreadyTaken_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Email Duplicado", "admin@emailduplicado.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Pedro Alves", "Ortopedia");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/" + doctor.id() + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest("admin@emailduplicado.com", "SenhaForte123"), adminHeaders),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void grantAccess_crossClinicDoctor_returns404() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica A Acesso", "admin@clinicaaacesso.com");
        DoctorResponse doctorA = createDoctor(clinicAHeaders, "Dr. Clinica A", "Clinico Geral");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica B Acesso", "admin@clinicabacesso.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/" + doctorA.id() + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest("intruso@clinicabacesso.com", "SenhaForte123"), clinicBHeaders),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void revokeAccess_happyPath_deletesLoginAndSubsequentLoginFails() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Revogar Acesso", "admin@revogaracesso.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Ana Lima", "Dermatologia");
        grantAccess(adminHeaders, doctor.id(), "ana.lima@revogaracesso.com", "SenhaForte123");

        ResponseEntity<Void> revokeResponse = restTemplate.exchange(
                url("/api/doctors/" + doctor.id() + "/access"), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), Void.class);
        assertThat(revokeResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ApiError> loginResponse = restTemplate.exchange(
                url("/api/auth/login"), HttpMethod.POST,
                new HttpEntity<>(new LoginRequest("ana.lima@revogaracesso.com", "SenhaForte123")), ApiError.class);
        assertThat(loginResponse.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void revokeAccess_noExistingAccess_returns404() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Revogar Sem Acesso", "admin@revogarsemacesso.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Carlos Reis", "Urologia");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/" + doctor.id() + "/access"), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void grantAccess_asDoctorToken_returns403() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Token Medico", "admin@tokenmedico.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Bia Costa", "Ginecologia");
        grantAccess(adminHeaders, doctor.id(), "bia.costa@tokenmedico.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("bia.costa@tokenmedico.com", "SenhaForte123");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/" + doctor.id() + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest("outro@tokenmedico.com", "SenhaForte123"), doctorHeaders),
                ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private ResponseEntity<DoctorAccessResponse> grantAccess(HttpHeaders adminHeaders, Long doctorId, String email, String password) {
        return restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest(email, password), adminHeaders), DoctorAccessResponse.class);
    }

    private HttpHeaders loginHeaders(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest(email, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().token());
        return headers;
    }

    private DoctorResponse createDoctor(HttpHeaders headers, String name, String specialty) {
        DoctorCreateRequest request = new DoctorCreateRequest(name, specialty, null, null);
        ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
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
