package com.senamed.backend.patient;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import com.senamed.backend.patient.dto.PatientCreateRequest;
import com.senamed.backend.patient.dto.PatientResponse;
import com.senamed.backend.patient.dto.PatientUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Fase 9 patient records: CRUD, search, soft delete + restore, ADMIN-only access, and
 * tenant isolation between two different clinics - mirrors {@code DoctorFlowIntegrationTest}'s
 * conventions.
 */
class PatientFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createPatient_happyPath_returns201WithPatient() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Pacientes Um", "admin1@pacientes.com");

        PatientCreateRequest request = new PatientCreateRequest(
                "Maria Souza", null, LocalDate.of(1990, 5, 12), "F", "12345678900", "maria@paciente.com",
                "11988887777", "01001-000", "Praca da Se", "1", null, "Se", "Sao Paulo", "SP",
                "Indicacao", "Paciente preferencial", true);
        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                url("/api/patients"), HttpMethod.POST, new HttpEntity<>(request, headers), PatientResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        PatientResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Maria Souza");
        assertThat(body.city()).isEqualTo("Sao Paulo");
        assertThat(body.active()).isTrue();
        assertThat(body.lgpdConsent()).isTrue();
        assertThat(body.lgpdConsentAt()).isNotNull();
    }

    @Test
    void createPatient_blankName_returns400() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Pacientes Validacao", "admin2@pacientes.com");

        PatientCreateRequest request = new PatientCreateRequest(
                "", null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/patients"), HttpMethod.POST, new HttpEntity<>(request, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listPatients_searchFiltersByName() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Pacientes Busca", "admin3@pacientes.com");
        createPatient(headers, "Ana Paula");
        createPatient(headers, "Bruno Costa");

        ResponseEntity<List<PatientResponse>> fullList = restTemplate.exchange(
                url("/api/patients"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<PatientResponse>>() {
                });
        assertThat(fullList.getBody()).extracting(PatientResponse::name).containsExactlyInAnyOrder("Ana Paula", "Bruno Costa");

        ResponseEntity<List<PatientResponse>> searchResponse = restTemplate.exchange(
                url("/api/patients?search=ana"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<PatientResponse>>() {
                });
        assertThat(searchResponse.getBody()).extracting(PatientResponse::name).containsExactly("Ana Paula");
    }

    @Test
    void updatePatient_editsFields() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Pacientes Edicao", "admin4@pacientes.com");
        Long patientId = createPatient(headers, "Nome Antigo").id();

        PatientUpdateRequest updateRequest = new PatientUpdateRequest(
                "Nome Novo", null, null, null, null, "novo@paciente.com", null, null, null, null, null, null,
                null, null, null, null, false);
        ResponseEntity<PatientResponse> updateResponse = restTemplate.exchange(
                url("/api/patients/" + patientId), HttpMethod.PUT, new HttpEntity<>(updateRequest, headers), PatientResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Nome Novo");
        assertThat(updateResponse.getBody().email()).isEqualTo("novo@paciente.com");
    }

    @Test
    void deactivateThenRestorePatient_softDeletesInsteadOfRemoving() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Pacientes Inativacao", "admin5@pacientes.com");
        Long patientId = createPatient(headers, "Paciente Inativado").id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/patients/" + patientId), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<PatientResponse> getResponse = restTemplate.exchange(
                url("/api/patients/" + patientId), HttpMethod.GET, new HttpEntity<>(headers), PatientResponse.class);
        assertThat(getResponse.getBody().active()).isFalse();

        ResponseEntity<PatientResponse> restoreResponse = restTemplate.exchange(
                url("/api/patients/" + patientId + "/restaurar"), HttpMethod.PATCH, new HttpEntity<>(headers), PatientResponse.class);
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoreResponse.getBody().active()).isTrue();
    }

    @Test
    void patients_areIsolatedBetweenClinics() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Pacientes A", "adminA@pacientes.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Pacientes B", "adminB@pacientes.com");

        Long patientOfClinicA = createPatient(clinicAHeaders, "Paciente Exclusivo A").id();

        ResponseEntity<ApiError> getResponse = restTemplate.exchange(
                url("/api/patients/" + patientOfClinicA), HttpMethod.GET, new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List<PatientResponse>> clinicBList = restTemplate.exchange(
                url("/api/patients"), HttpMethod.GET, new HttpEntity<>(clinicBHeaders),
                new ParameterizedTypeReference<List<PatientResponse>>() {
                });
        assertThat(clinicBList.getBody()).isEmpty();
    }

    @Test
    void patients_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/patients"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void patients_asDoctorRole_returns403() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Pacientes Restricao Medico", "admin6@pacientes.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Restrito", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor@pacientes.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("doutor@pacientes.com", "SenhaForte123");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/patients"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private PatientResponse createPatient(HttpHeaders headers, String name) {
        PatientCreateRequest request = new PatientCreateRequest(
                name, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, false);
        ResponseEntity<PatientResponse> response = restTemplate.exchange(
                url("/api/patients"), HttpMethod.POST, new HttpEntity<>(request, headers), PatientResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private DoctorResponse createDoctor(HttpHeaders headers, String name, String specialty) {
        DoctorCreateRequest request = new DoctorCreateRequest(name, specialty, null, null);
        ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void grantAccess(HttpHeaders adminHeaders, Long doctorId, String email, String password) {
        ResponseEntity<DoctorAccessResponse> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest(email, password, null), adminHeaders), DoctorAccessResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private HttpHeaders loginHeaders(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest(email, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().token());
        return headers;
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
