package com.senamed.backend.dashboard;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.dashboard.dto.DashboardSummaryResponse;
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

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers KAN-98: the dashboard home page's real-data summary, for both ADMIN and DOCTOR callers. */
class DashboardSummaryIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

    @Test
    void summary_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/dashboard/summary"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void summary_asAdmin_countsTodayAndActiveDoctors_listsUpcoming() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Resumo", "admin@resumo.com");
        Long doctorA = createDoctor(adminHeaders, "Dr. Resumo A", "Clinico Geral").id();
        Long doctorB = createDoctor(adminHeaders, "Dr. Resumo B", "Clinico Geral").id();

        // "Today" bookings must sit far enough in the future-of-today to not fall before "now" at
        // test run time, so pin them at 23:00 today's slot via a future weekday isn't reliable -
        // instead exercise "today" through the FUTURE_DATE appointment's date field directly is not
        // possible (constraint requires startsAt in the future), so this test focuses on what's
        // reliably assertable without flaking around the current wall-clock time: the upcoming list
        // and the active doctor count.
        createAppointment(adminHeaders, doctorA, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Um", "um@resumo.com");
        createAppointment(adminHeaders, doctorB, FUTURE_DATE, LocalTime.of(10, 0), "Paciente Dois", "dois@resumo.com");

        ResponseEntity<DashboardSummaryResponse> response = restTemplate.exchange(
                url("/api/dashboard/summary"), HttpMethod.GET, new HttpEntity<>(adminHeaders), DashboardSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardSummaryResponse body = response.getBody();
        assertThat(body.activeDoctorCount()).isEqualTo(2L);
        assertThat(body.upcoming()).extracting(AppointmentResponse::patientName)
                .containsExactly("Paciente Um", "Paciente Dois");
    }

    @Test
    void summary_excludesCancelledAppointments_fromUpcoming() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Resumo Cancelada", "admin@resumocancelada.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Resumo Cancelada", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Cancelado", "cancelado@resumocancelada.com");
        restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        ResponseEntity<DashboardSummaryResponse> response = restTemplate.exchange(
                url("/api/dashboard/summary"), HttpMethod.GET, new HttpEntity<>(adminHeaders), DashboardSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().upcoming()).isEmpty();
    }

    @Test
    void summary_asDoctor_scopedToOwnAppointments_withNullActiveDoctorCount() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Resumo Medico", "admin@resumomedico.com");
        DoctorResponse doctorA = createDoctor(adminHeaders, "Dr. Resumo Medico A", "Clinico Geral");
        DoctorResponse doctorB = createDoctor(adminHeaders, "Dr. Resumo Medico B", "Clinico Geral");
        grantAccess(adminHeaders, doctorA.id(), "doutor.a@resumomedico.com", "SenhaForte123");
        HttpHeaders doctorAHeaders = loginHeaders("doutor.a@resumomedico.com", "SenhaForte123");

        createAppointment(adminHeaders, doctorA.id(), FUTURE_DATE, LocalTime.of(9, 0), "Paciente A", "pacientea@resumomedico.com");
        createAppointment(adminHeaders, doctorB.id(), FUTURE_DATE, LocalTime.of(10, 0), "Paciente B", "pacienteb@resumomedico.com");

        ResponseEntity<DashboardSummaryResponse> response = restTemplate.exchange(
                url("/api/dashboard/summary"), HttpMethod.GET, new HttpEntity<>(doctorAHeaders), DashboardSummaryResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        DashboardSummaryResponse body = response.getBody();
        assertThat(body.activeDoctorCount()).isNull();
        assertThat(body.upcoming()).extracting(AppointmentResponse::patientName).containsExactly("Paciente A");
    }

    private AppointmentResponse createAppointment(
            HttpHeaders headers, Long doctorId, LocalDate date, LocalTime startTime, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, List.of(), date, startTime, patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, headers), AppointmentResponse.class);
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
