package com.senamed.backend.doctor;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.DoctorUpdateRequest;
import com.senamed.backend.doctor.dto.TimeOffRequest;
import com.senamed.backend.doctor.dto.TimeOffResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Fase 2 doctor management (RF-00x/RN-015): create up to the plan limit and the (N+1)-th
 * failing, soft delete, editing, weekly availability (replace-all semantics), accumulating
 * time-off periods, and tenant isolation between two different clinics.
 */
class DoctorFlowIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createDoctor_happyPath_returns201WithDoctor() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Um", "admin1@clinica.com");

        DoctorCreateRequest request = new DoctorCreateRequest("Dr. Joao Silva", "Cardiologia", "joao@clinica.com", "11988887777");
        ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        DoctorResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.name()).isEqualTo("Dr. Joao Silva");
        assertThat(body.specialty()).isEqualTo("Cardiologia");
        assertThat(body.active()).isTrue();
    }

    @Test
    void createDoctors_upToMaxDoctors_thenNPlus1thFails_with422() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Limite", "admin2@clinica.com");

        // default max_doctors placeholder is 3
        for (int i = 1; i <= 3; i++) {
            DoctorCreateRequest request = new DoctorCreateRequest("Dr. Medico " + i, "Clinico Geral", null, null);
            ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                    url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        }

        DoctorCreateRequest fourth = new DoctorCreateRequest("Dr. Medico 4", "Clinico Geral", null, null);
        ResponseEntity<ApiError> overLimitResponse = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(fourth, headers), ApiError.class);

        assertThat(overLimitResponse.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_ENTITY);
        assertThat(overLimitResponse.getBody().message()).contains("Limite de médicos do plano atingido").contains("3");

        // deactivating one active doctor frees a slot for RN-015 purposes
        ResponseEntity<List<DoctorResponse>> listResponse = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<DoctorResponse>>() {
                });
        Long firstDoctorId = listResponse.getBody().get(0).id();
        restTemplate.exchange(url("/api/doctors/" + firstDoctorId), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);

        ResponseEntity<DoctorResponse> afterFreeingSlot = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(fourth, headers), DoctorResponse.class);
        assertThat(afterFreeingSlot.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void deactivateDoctor_softDeletesInsteadOfRemoving() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Inativacao", "admin3@clinica.com");
        Long doctorId = createDoctor(headers, "Dr. Carlos", "Pediatria").id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<DoctorResponse> getResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId), HttpMethod.GET, new HttpEntity<>(headers), DoctorResponse.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().active()).isFalse(); // row still exists, just inactive
    }

    @Test
    void updateDoctor_editsFields() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Edicao", "admin4@clinica.com");
        Long doctorId = createDoctor(headers, "Dr. Antigo Nome", "Dermatologia").id();

        DoctorUpdateRequest updateRequest = new DoctorUpdateRequest(
                "Dr. Novo Nome", "Ortopedia", "novo@clinica.com", "11977776666");
        ResponseEntity<DoctorResponse> updateResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId), HttpMethod.PUT, new HttpEntity<>(updateRequest, headers), DoctorResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Dr. Novo Nome");
        assertThat(updateResponse.getBody().specialty()).isEqualTo("Ortopedia");
        assertThat(updateResponse.getBody().email()).isEqualTo("novo@clinica.com");
    }

    @Test
    void setAvailability_replacesAllWindows_andRejectsInvalidTimeRange() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Disponibilidade", "admin5@clinica.com");
        Long doctorId = createDoctor(headers, "Dr. Agenda", "Clinico Geral").id();

        List<AvailabilityRequest> firstSet = List.of(
                new AvailabilityRequest(1, LocalTime.of(8, 0), LocalTime.of(12, 0)),
                new AvailabilityRequest(1, LocalTime.of(14, 0), LocalTime.of(18, 0)));
        ResponseEntity<List<AvailabilityResponse>> firstResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(firstSet, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() {
                });
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(firstResponse.getBody()).hasSize(2);

        // replacing again must wipe the previous set (only one window survives)
        List<AvailabilityRequest> secondSet = List.of(new AvailabilityRequest(3, LocalTime.of(9, 0), LocalTime.of(11, 0)));
        restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(secondSet, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() {
                });

        ResponseEntity<List<AvailabilityResponse>> listResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() {
                });
        assertThat(listResponse.getBody()).hasSize(1);
        assertThat(listResponse.getBody().get(0).dayOfWeek()).isEqualTo(3);

        // invalid range (startTime >= endTime) -> 400
        List<AvailabilityRequest> invalidSet = List.of(new AvailabilityRequest(2, LocalTime.of(18, 0), LocalTime.of(8, 0)));
        ResponseEntity<ApiError> invalidResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(invalidSet, headers),
                ApiError.class);
        assertThat(invalidResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createTimeOff_accumulatesRecords_andDefaultsEndDateToStartDate() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Folgas", "admin6@clinica.com");
        Long doctorId = createDoctor(headers, "Dr. Ferias", "Clinico Geral").id();

        TimeOffRequest singleDay = new TimeOffRequest(LocalDate.of(2026, 8, 10), null, "Consulta pessoal");
        ResponseEntity<TimeOffResponse> firstResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/time-off"), HttpMethod.POST, new HttpEntity<>(singleDay, headers),
                TimeOffResponse.class);
        assertThat(firstResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(firstResponse.getBody().endDate()).isEqualTo(LocalDate.of(2026, 8, 10));

        TimeOffRequest vacation = new TimeOffRequest(LocalDate.of(2026, 12, 20), LocalDate.of(2026, 12, 31), "Ferias");
        restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/time-off"), HttpMethod.POST, new HttpEntity<>(vacation, headers),
                TimeOffResponse.class);

        ResponseEntity<List<TimeOffResponse>> listResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/time-off"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<TimeOffResponse>>() {
                });
        assertThat(listResponse.getBody()).hasSize(2); // both records accumulate, none is replaced
    }

    @Test
    void doctorsAndTheirData_areIsolatedBetweenClinics() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica A", "adminA@clinica.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica B", "adminB@clinica.com");

        Long doctorOfClinicA = createDoctor(clinicAHeaders, "Dr. Exclusivo A", "Cardiologia").id();

        // clinic B cannot read clinic A's doctor
        ResponseEntity<ApiError> getResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorOfClinicA), HttpMethod.GET, new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ... nor update it
        DoctorUpdateRequest hijackAttempt = new DoctorUpdateRequest("Hijacked", null, null, null);
        ResponseEntity<ApiError> updateResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorOfClinicA), HttpMethod.PUT, new HttpEntity<>(hijackAttempt, clinicBHeaders),
                ApiError.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ... nor deactivate it
        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/doctors/" + doctorOfClinicA), HttpMethod.DELETE, new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // ... nor see it in its own doctor list
        ResponseEntity<List<DoctorResponse>> clinicBList = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.GET, new HttpEntity<>(clinicBHeaders),
                new ParameterizedTypeReference<List<DoctorResponse>>() {
                });
        assertThat(clinicBList.getBody()).isEmpty();

        // clinic A still sees its own doctor untouched
        ResponseEntity<DoctorResponse> clinicAGet = restTemplate.exchange(
                url("/api/doctors/" + doctorOfClinicA), HttpMethod.GET, new HttpEntity<>(clinicAHeaders), DoctorResponse.class);
        assertThat(clinicAGet.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(clinicAGet.getBody().name()).isEqualTo("Dr. Exclusivo A");
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
