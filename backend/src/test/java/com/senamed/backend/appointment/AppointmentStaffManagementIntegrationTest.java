package com.senamed.backend.appointment;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentRescheduleRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
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

import java.time.LocalDate;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers KAN-93/KAN-95/KAN-97: staff (ADMIN) creating, cancelling and rescheduling an appointment
 * manually from the dashboard, as opposed to the public patient-facing flow covered by
 * {@link AppointmentFlowIntegrationTest}.
 */
class AppointmentStaffManagementIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

    @Test
    void create_asAdmin_happyPath_returns201() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Criacao Staff", "admin@criacaostaff.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Balcao", "Clinico Geral").id();

        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Telefone", "telefone@paciente.com");

        assertThat(appointment.id()).isNotNull();
        assertThat(appointment.doctorId()).isEqualTo(doctorId);
        assertThat(appointment.patientName()).isEqualTo("Paciente Telefone");
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void create_withoutLgpdConsent_returns400() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Sem Consentimento", "admin@staffsemconsentimento.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Sem Consentimento", "Clinico Geral").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffsemconsentimento.com", null, false);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).containsIgnoringCase("LGPD");
    }

    @Test
    void create_doctorFromAnotherClinic_returns404() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Staff A", "admin@staffa.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Staff B", "admin@staffb.com");
        Long doctorFromB = createDoctor(clinicBHeaders, "Dr. De Outra Clinica", "Clinico Geral").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorFromB, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffa.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, clinicAHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_slotAlreadyTaken_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Conflito", "admin@staffconflito.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Conflito Staff", "Clinico Geral").id();
        createAppointment(adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Primeiro", "primeiro@staffconflito.com");

        AppointmentCreateRequest secondRequest = new AppointmentCreateRequest(
                doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Segundo", "segundo@staffconflito.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(secondRequest, adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_withoutToken_returns401() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@semtoken.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/appointments"), request, ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void create_asDoctorRole_returns403() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Restricao Medico", "admin@staffrestricaomedico.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Restrito Staff", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor@staffrestricaomedico.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("doutor@staffrestricaomedico.com", "SenhaForte123");

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctor.id(), FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffrestricaomedico.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, doctorHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    void cancel_asAdmin_success_thenSecondCancelAttempt_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Cancelamento", "admin@staffcancelamento.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Cancela Staff", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Cancela", "cancela@staffcancelamento.com");

        ResponseEntity<AppointmentResponse> cancelResponse = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().status()).isEqualTo(AppointmentStatus.CANCELLED);

        ResponseEntity<ApiError> secondCancelResponse = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(adminHeaders), ApiError.class);
        assertThat(secondCancelResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancel_appointmentFromAnotherClinic_returns404() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Staff Cancel A", "admin@staffcancela.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Staff Cancel B", "admin@staffcancelb.com");
        Long doctorB = createDoctor(clinicBHeaders, "Dr. B Cancela", "Clinico Geral").id();
        AppointmentResponse appointmentB = createAppointment(
                clinicBHeaders, doctorB, FUTURE_DATE, LocalTime.of(9, 0), "Paciente B", "pacienteb@staffcancelb.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + appointmentB.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(clinicAHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reschedule_asAdmin_happyPath_movesSlot() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Reagenda", "admin@staffreagenda.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Reagenda", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Reagenda", "reagenda@staffreagenda.com");

        LocalDate newDate = FUTURE_DATE.plusDays(1);
        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(newDate, LocalTime.of(11, 0));
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id()), HttpMethod.PATCH,
                new HttpEntity<>(request, adminHeaders), AppointmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().date()).isEqualTo(newDate);
        assertThat(response.getBody().startTime()).isEqualTo("11:00");
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.CONFIRMED);
    }

    @Test
    void reschedule_ontoAnotherAppointmentsSlot_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Reagenda Conflito", "admin@staffreagendaconflito.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Reagenda Conflito", "Clinico Geral").id();
        createAppointment(adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Ocupante", "ocupante@staffreagendaconflito.com");
        AppointmentResponse toMove = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(10, 0), "Paciente Movel", "movel@staffreagendaconflito.com");

        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(FUTURE_DATE, LocalTime.of(9, 0));
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + toMove.id()), HttpMethod.PATCH,
                new HttpEntity<>(request, adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void reschedule_cancelledAppointment_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Reagenda Cancelada", "admin@staffreagendacancelada.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Reagenda Cancelada", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffreagendacancelada.com");
        restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        AppointmentRescheduleRequest request = new AppointmentRescheduleRequest(FUTURE_DATE.plusDays(1), LocalTime.of(9, 0));
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id()), HttpMethod.PATCH,
                new HttpEntity<>(request, adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    private AppointmentResponse createAppointment(
            HttpHeaders headers, Long doctorId, LocalDate date, LocalTime startTime, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, date, startTime, patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void grantAccess(HttpHeaders adminHeaders, Long doctorId, String email, String password) {
        ResponseEntity<DoctorAccessResponse> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest(email, password), adminHeaders), DoctorAccessResponse.class);
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
