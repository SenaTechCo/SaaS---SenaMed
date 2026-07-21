package com.senamed.backend.appointment;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentRescheduleRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import com.senamed.backend.finance.dto.ReceivableResponse;
import com.senamed.backend.patient.dto.PatientCreateRequest;
import com.senamed.backend.patient.dto.PatientResponse;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

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
    void create_withValidPatientId_linksPatient_returns201() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Vinculo Paciente", "admin@vinculopaciente.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Vinculo", "Clinico Geral").id();
        Long patientId = createPatient(adminHeaders, "Paciente Cadastrado").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, patientId, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Cadastrado", "cadastrado@vinculopaciente.com", null, true);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders), AppointmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().patientId()).isEqualTo(patientId);
    }

    @Test
    void create_patientIdFromAnotherClinic_returns404() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Vinculo A", "admin@vinculoa.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Vinculo B", "admin@vinculob.com");
        Long doctorId = createDoctor(clinicAHeaders, "Dr. Vinculo A", "Clinico Geral").id();
        Long patientFromB = createPatient(clinicBHeaders, "Paciente De Outra Clinica").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, patientFromB, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@vinculoa.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, clinicAHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void create_withoutPatientId_stillWorks_patientIdIsNull() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sem Vinculo Paciente", "admin@semvinculopaciente.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Sem Vinculo", "Clinico Geral").id();

        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Sem Vinculo", "semvinculo@semvinculopaciente.com");

        assertThat(appointment.id()).isNotNull();
        assertThat(appointment.patientId()).isNull();
        assertThat(appointment.patientName()).isEqualTo("Paciente Sem Vinculo");
    }

    @Test
    void create_withoutLgpdConsent_returns400() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Staff Sem Consentimento", "admin@staffsemconsentimento.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Sem Consentimento", "Clinico Geral").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffsemconsentimento.com", null, false);
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
                doctorFromB, null, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffa.com", null, true);
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
                doctorId, null, null, FUTURE_DATE, LocalTime.of(9, 0), "Segundo", "segundo@staffconflito.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(secondRequest, adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void create_withoutToken_returns401() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                1L, null, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@semtoken.com", null, true);
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
                doctor.id(), null, null, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@staffrestricaomedico.com", null, true);
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

    @Test
    void create_withServiceId_copiesPriceAndServiceName() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Servico Agendamento", "admin@servicoagendamento.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Servico", "Clinico Geral").id();
        ServiceOfferingResponse service = createServiceOffering(adminHeaders, "Consulta Inicial", "150.00");

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, service.id(), FUTURE_DATE, LocalTime.of(9, 0),
                "Paciente Servico", "servico@servicoagendamento.com", null, true);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders), AppointmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().serviceId()).isEqualTo(service.id());
        assertThat(response.getBody().serviceName()).isEqualTo("Consulta Inicial");
        assertThat(response.getBody().price()).isEqualByComparingTo(new BigDecimal("150.00"));
    }

    @Test
    void markAttended_confirmedAppointment_becomesAttended() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Atender", "admin@atender.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Atender", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Atender", "atender@atender.com");

        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.ATTENDED);
    }

    @Test
    void markAttended_alreadyAttended_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Atender Duplo", "admin@atenderduplo.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Atender Duplo", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Atender Duplo", "atenderduplo@atenderduplo.com");
        restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markAttended_cancelledAppointment_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Atender Cancelado", "admin@atendercancelado.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Atender Cancelado", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Atender Cancelado", "atendercancelado@atendercancelado.com");
        restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markNoShow_confirmedAppointment_becomesNoShow() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Falta", "admin@falta.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Falta", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Falta", "falta@falta.com");

        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/faltou"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(AppointmentStatus.NO_SHOW);
    }

    @Test
    void markNoShow_alreadyAttended_returns409() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Falta Atendido", "admin@faltaatendido.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Falta Atendido", "Clinico Geral").id();
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Falta Atendido", "faltaatendido@faltaatendido.com");
        restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/appointments/" + appointment.id() + "/faltou"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void markNoShow_doesNotCreateReceivable() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Falta Financeiro", "admin@faltafinanceiro.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Falta Financeiro", "Clinico Geral").id();
        ServiceOfferingResponse service = createServiceOffering(adminHeaders, "Consulta Falta", "150.00");

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, service.id(), FUTURE_DATE, LocalTime.of(9, 0),
                "Paciente Falta Financeiro", "faltafinanceiro@faltafinanceiro.com", null, true);
        ResponseEntity<AppointmentResponse> createResponse = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, adminHeaders), AppointmentResponse.class);
        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        restTemplate.exchange(
                url("/api/appointments/" + createResponse.getBody().id() + "/faltou"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), AppointmentResponse.class);

        ResponseEntity<List<ReceivableResponse>> receivablesResponse = restTemplate.exchange(
                url("/api/finance/receivables"), HttpMethod.GET, new HttpEntity<>(adminHeaders),
                new ParameterizedTypeReference<List<ReceivableResponse>>() {
                });
        assertThat(receivablesResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(receivablesResponse.getBody()).isEmpty();
    }

    private AppointmentResponse createAppointment(
            HttpHeaders headers, Long doctorId, LocalDate date, LocalTime startTime, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, null, date, startTime, patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.POST, new HttpEntity<>(request, headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ServiceOfferingResponse createServiceOffering(HttpHeaders headers, String name, String price) {
        ServiceOfferingCreateRequest request = new ServiceOfferingCreateRequest(name, null, 30, new BigDecimal(price));
        ResponseEntity<ServiceOfferingResponse> response = restTemplate.exchange(
                url("/api/services"), HttpMethod.POST, new HttpEntity<>(request, headers), ServiceOfferingResponse.class);
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

    private PatientResponse createPatient(HttpHeaders headers, String name) {
        PatientCreateRequest request = new PatientCreateRequest(
                name, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, true);
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
