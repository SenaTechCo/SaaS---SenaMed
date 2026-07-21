package com.senamed.backend.doctor;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.AppointmentStatus;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
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

/** Covers KAN-77: doctor login via the shared /api/auth/login, and the read-only /api/doctors/me/** views. */
class DoctorSelfServiceIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Test
    void login_asDoctor_returnsJwtWithDoctorId() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Login Medico", "admin@loginmedico.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. A", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor.a@loginmedico.com", "SenhaForte123");

        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest("doutor.a@loginmedico.com", "SenhaForte123"), AuthResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().user().role().name()).isEqualTo("DOCTOR");
        assertThat(response.getBody().user().doctorId()).isEqualTo(doctor.id());
    }

    @Test
    void selfServiceEndpoints_returnOnlyOwnData_notOtherDoctors() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Autoatendimento", "admin@autoatendimento.com");

        DoctorResponse doctorA = createDoctor(adminHeaders, "Dr. Autoatendimento A", "Clinico Geral");
        setAvailability(adminHeaders, doctorA.id(), FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(11, 0));
        createTimeOff(adminHeaders, doctorA.id(), FUTURE_DATE.plusDays(20), "Ferias A");
        AppointmentResponse appointmentA = bookAppointment(doctorA.id(), FUTURE_DATE, LocalTime.of(9, 0), "Paciente A", "pacientea@autoatendimento.com");
        grantAccess(adminHeaders, doctorA.id(), "doutor.a@autoatendimento.com", "SenhaForte123");
        HttpHeaders doctorAHeaders = loginHeaders("doutor.a@autoatendimento.com", "SenhaForte123");

        DoctorResponse doctorB = createDoctor(adminHeaders, "Dr. Autoatendimento B", "Clinico Geral");
        setAvailability(adminHeaders, doctorB.id(), FUTURE_DAY_OF_WEEK, LocalTime.of(13, 0), LocalTime.of(15, 0));
        createTimeOff(adminHeaders, doctorB.id(), FUTURE_DATE.plusDays(25), "Ferias B");
        bookAppointment(doctorB.id(), FUTURE_DATE, LocalTime.of(13, 0), "Paciente B", "pacienteb@autoatendimento.com");
        grantAccess(adminHeaders, doctorB.id(), "doutor.b@autoatendimento.com", "SenhaForte123");

        ResponseEntity<DoctorResponse> profileResponse = restTemplate.exchange(
                url("/api/doctors/me"), HttpMethod.GET, new HttpEntity<>(doctorAHeaders), DoctorResponse.class);
        assertThat(profileResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(profileResponse.getBody().id()).isEqualTo(doctorA.id());

        ResponseEntity<List<AppointmentResponse>> appointmentsResponse = restTemplate.exchange(
                url("/api/doctors/me/appointments"), HttpMethod.GET, new HttpEntity<>(doctorAHeaders),
                new ParameterizedTypeReference<List<AppointmentResponse>>() { });
        assertThat(appointmentsResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(appointmentsResponse.getBody()).extracting(AppointmentResponse::id).containsExactly(appointmentA.id());
        assertThat(appointmentsResponse.getBody()).extracting(AppointmentResponse::status).containsExactly(AppointmentStatus.CONFIRMED);

        ResponseEntity<List<AvailabilityResponse>> availabilityResponse = restTemplate.exchange(
                url("/api/doctors/me/availability"), HttpMethod.GET, new HttpEntity<>(doctorAHeaders),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() { });
        assertThat(availabilityResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(availabilityResponse.getBody()).extracting(AvailabilityResponse::startTime).containsExactly(LocalTime.of(9, 0));

        ResponseEntity<List<TimeOffResponse>> timeOffResponse = restTemplate.exchange(
                url("/api/doctors/me/time-off"), HttpMethod.GET, new HttpEntity<>(doctorAHeaders),
                new ParameterizedTypeReference<List<TimeOffResponse>>() { });
        assertThat(timeOffResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(timeOffResponse.getBody()).extracting(TimeOffResponse::reason).containsExactly("Ferias A");
    }

    @Test
    void doctorMeEndpoints_asAdminToken_return403() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Admin Sem Doctor", "admin@adminsemdoctor.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/me"), HttpMethod.GET, new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void doctorToken_cannotCallAdminOnlyDoctorEndpoints() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Restricao Medico", "admin@restricaomedico.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Restrito", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor.restrito@restricaomedico.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("doutor.restrito@restricaomedico.com", "SenhaForte123");

        // Read-only doctor list stays open to any authenticated user (KAN permission reform) -
        // any staff member needs it to build an appointment, regardless of PERM_MANAGE_USERS.
        // (String.class here, not ApiError.class, since a 200 response body is a JSON array, not
        // an error shape.)
        assertThat(restTemplate.exchange(
                url("/api/doctors"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), String.class)
                .getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST,
                new HttpEntity<>(new DoctorCreateRequest("Dr. Intruso", "Clinico Geral", null, null), doctorHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(
                url("/api/doctors/" + doctor.id()), HttpMethod.DELETE, new HttpEntity<>(doctorHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void doctorToken_cannotUpdateClinic_butCanReadIt() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Restricao Perfil", "admin@restricaoperfil.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Perfil", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor.perfil@restricaoperfil.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("doutor.perfil@restricaoperfil.com", "SenhaForte123");

        ResponseEntity<ApiError> updateResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.PUT,
                new HttpEntity<>(new ClinicUpdateRequest(
                        "Nome Hackeado", null, null, null, "America/Sao_Paulo", null, null, null, null), doctorHeaders),
                ApiError.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        ResponseEntity<String> getResponse = restTemplate.exchange(
                url("/api/clinics/me"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), String.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
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

    private AppointmentResponse bookAppointment(Long doctorId, LocalDate date, LocalTime startTime, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, List.of(), date, startTime, patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                url("/api/public/appointments"), request, AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void setAvailability(HttpHeaders headers, Long doctorId, int dayOfWeek, LocalTime start, LocalTime end) {
        List<AvailabilityRequest> windows = List.of(new AvailabilityRequest(dayOfWeek, start, end));
        ResponseEntity<List<AvailabilityResponse>> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(windows, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void createTimeOff(HttpHeaders headers, Long doctorId, LocalDate startDate, String reason) {
        ResponseEntity<TimeOffResponse> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/time-off"), HttpMethod.POST,
                new HttpEntity<>(new TimeOffRequest(startDate, null, reason), headers), TimeOffResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
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
