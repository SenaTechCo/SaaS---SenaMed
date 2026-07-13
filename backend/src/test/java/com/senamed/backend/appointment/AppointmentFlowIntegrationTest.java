package com.senamed.backend.appointment;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.dto.AvailableSlotsResponse;
import com.senamed.backend.appointment.dto.PublicClinicResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.TimeOffRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Fase 3 public scheduling (KAN-53..KAN-59): the public clinic page, available-slots
 * computation (weekly availability minus time-off minus already-booked appointments), booking
 * with LGPD consent, slot-conflict handling, cancellation (including the 24h window rule), and
 * tenant isolation between two clinics' public pages.
 */
class AppointmentFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Always a Monday-Sunday-agnostic date at least a week out, so it's never "today" and never
     *  within the 24h cancellation window - avoids flakiness around the current time of day. */
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Test
    void publicClinicPage_listsOnlyActiveDoctorsOfThatClinic_andIsolatesFromOtherClinics() {
        ClinicSession clinicA = registerClinic("Clinica Publica A", "adminA@publica.com");
        ClinicSession clinicB = registerClinic("Clinica Publica B", "adminB@publica.com");

        Long activeDoctorA = createDoctor(clinicA.headers, "Dr. Ativo A", "Cardiologia").id();
        Long inactiveDoctorA = createDoctor(clinicA.headers, "Dr. Inativo A", "Dermatologia").id();
        restTemplate.exchange(
                url("/api/doctors/" + inactiveDoctorA), HttpMethod.DELETE, new HttpEntity<>(clinicA.headers), Void.class);
        createDoctor(clinicB.headers, "Dr. Exclusivo B", "Pediatria");

        ResponseEntity<PublicClinicResponse> response = restTemplate.getForEntity(
                url("/api/public/clinics/" + clinicA.slug), PublicClinicResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        PublicClinicResponse body = response.getBody();
        assertThat(body.name()).isEqualTo("Clinica Publica A");
        assertThat(body.doctors()).extracting("id").containsExactly(activeDoctorA);
        assertThat(body.doctors()).extracting("name").doesNotContain("Dr. Exclusivo B", "Dr. Inativo A");
    }

    @Test
    void publicClinicPage_unknownSlug_returns404() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/public/clinics/clinica-que-nao-existe"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void availableSlots_combinesAvailability_timeOff_andBookedAppointments() {
        ClinicSession clinic = registerClinic("Clinica Agenda Publica", "admin@agendapublica.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Horarios", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(11, 0));

        AvailableSlotsResponse initialSlots = fetchAvailableSlots(doctorId, FUTURE_DATE);
        assertThat(initialSlots.slots()).containsExactly("09:00", "09:30", "10:00", "10:30");

        bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 30), "Paciente Um", "um@paciente.com");

        AvailableSlotsResponse afterBooking = fetchAvailableSlots(doctorId, FUTURE_DATE);
        assertThat(afterBooking.slots()).containsExactly("09:00", "10:00", "10:30");

        LocalDate timeOffDate = FUTURE_DATE.plusDays(7);
        Long doctorForTimeOff = createDoctor(clinic.headers, "Dr. Ferias", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorForTimeOff, timeOffDate.getDayOfWeek().getValue(), LocalTime.of(9, 0), LocalTime.of(11, 0));
        restTemplate.exchange(
                url("/api/doctors/" + doctorForTimeOff + "/time-off"), HttpMethod.POST,
                new HttpEntity<>(new TimeOffRequest(timeOffDate, timeOffDate, "Ferias"), clinic.headers), Void.class);

        AvailableSlotsResponse duringTimeOff = fetchAvailableSlots(doctorForTimeOff, timeOffDate);
        assertThat(duringTimeOff.slots()).isEmpty();
    }

    @Test
    void availableSlots_pastDate_returns400() {
        ClinicSession clinic = registerClinic("Clinica Data Passada", "admin@datapassada.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Passado", "Clinico Geral").id();

        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/public/doctors/" + doctorId + "/available-slots?date=" + LocalDate.now().minusDays(1)),
                ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void availableSlots_inactiveOrUnknownDoctor_returns404() {
        ClinicSession clinic = registerClinic("Clinica Medico Inativo", "admin@medicoinativo.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Sera Inativado", "Clinico Geral").id();
        restTemplate.exchange(url("/api/doctors/" + doctorId), HttpMethod.DELETE, new HttpEntity<>(clinic.headers), Void.class);

        ResponseEntity<ApiError> response = restTemplate.getForEntity(
                url("/api/public/doctors/" + doctorId + "/available-slots?date=" + FUTURE_DATE), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> unknownResponse = restTemplate.getForEntity(
                url("/api/public/doctors/999999/available-slots?date=" + FUTURE_DATE), ApiError.class);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void createAppointment_happyPath_returns201WithCancelToken() {
        ClinicSession clinic = registerClinic("Clinica Agendamento Feliz", "admin@feliz.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Feliz", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(14, 0), LocalTime.of(15, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(14, 0), "Maria Paciente", "maria@paciente.com");

        assertThat(appointment.id()).isNotNull();
        assertThat(appointment.doctorId()).isEqualTo(doctorId);
        assertThat(appointment.clinicName()).isEqualTo("Clinica Agendamento Feliz");
        assertThat(appointment.startTime()).isEqualTo("14:00");
        assertThat(appointment.endTime()).isEqualTo("14:30");
        assertThat(appointment.status()).isEqualTo(AppointmentStatus.CONFIRMED);
        assertThat(appointment.cancelToken()).isNotBlank();
        assertThat(UUID.fromString(appointment.cancelToken())).isNotNull();
    }

    @Test
    void createAppointment_withoutLgpdConsent_returns400() {
        ClinicSession clinic = registerClinic("Clinica Sem Consentimento", "admin@semconsentimento.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Consentimento", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Sem Consentimento", "semconsentimento@paciente.com", null, false);
        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/public/appointments"), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().message()).containsIgnoringCase("LGPD");
    }

    @Test
    void createAppointment_pastDateTime_returns400() {
        ClinicSession clinic = registerClinic("Clinica Data Passada Post", "admin@datapassadapost.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Passado Post", "Clinico Geral").id();

        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, LocalDate.now().minusDays(1), LocalTime.of(9, 0), "Paciente Passado", "passado@paciente.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/public/appointments"), request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void createAppointment_slotAlreadyTaken_returns409() {
        ClinicSession clinic = registerClinic("Clinica Conflito", "admin@conflito.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Conflito", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Primeiro Paciente", "primeiro@paciente.com");

        AppointmentCreateRequest secondRequest = new AppointmentCreateRequest(
                doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Segundo Paciente", "segundo@paciente.com", null, true);
        ResponseEntity<ApiError> conflictResponse = restTemplate.postForEntity(
                url("/api/public/appointments"), secondRequest, ApiError.class);

        assertThat(conflictResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createAppointment_inactiveOrUnknownDoctor_returns404() {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                999999L, FUTURE_DATE, LocalTime.of(9, 0), "Paciente", "paciente@teste.com", null, true);
        ResponseEntity<ApiError> response = restTemplate.postForEntity(url("/api/public/appointments"), request, ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void cancelAppointment_success_thenSecondCancelAttempt_returns409() {
        ClinicSession clinic = registerClinic("Clinica Cancelamento", "admin@cancelamento.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Cancelamento", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Cancela", "cancela@paciente.com");

        ResponseEntity<AppointmentResponse> cancelResponse = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + appointment.cancelToken()), null, AppointmentResponse.class);
        assertThat(cancelResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(cancelResponse.getBody().status()).isEqualTo(AppointmentStatus.CANCELLED);

        // the slot is free again once cancelled
        AvailableSlotsResponse slotsAfterCancel = fetchAvailableSlots(doctorId, FUTURE_DATE);
        assertThat(slotsAfterCancel.slots()).contains("09:00");

        ResponseEntity<ApiError> secondCancelResponse = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + appointment.cancelToken()), null, ApiError.class);
        assertThat(secondCancelResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondCancelResponse.getBody().message()).contains("já foi cancelado");
    }

    @Test
    void cancelAppointment_within24Hours_returns409() {
        ClinicSession clinic = registerClinic("Clinica Cancelamento Tardio", "admin@cancelamentotardio.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Tardio", "Clinico Geral").id();
        UUID token = UUID.randomUUID();

        LocalDateTime startsAt = LocalDateTime.now().plusHours(5);
        jdbcTemplate.update(
                "INSERT INTO appointments (clinic_id, doctor_id, patient_name, patient_email, patient_phone, "
                        + "starts_at, ends_at, status, lgpd_consent_at, cancel_token) "
                        + "VALUES (?, ?, ?, ?, ?, ?, ?, 'CONFIRMED', now(), ?)",
                clinic.clinicId, doctorId, "Paciente Urgente", "urgente@paciente.com", null,
                Timestamp.valueOf(startsAt), Timestamp.valueOf(startsAt.plusMinutes(30)), token);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + token), null, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("24h");
    }

    @Test
    void cancelAppointment_unknownOrMalformedToken_returns404() {
        ResponseEntity<ApiError> unknownTokenResponse = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + UUID.randomUUID()), null, ApiError.class);
        assertThat(unknownTokenResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> malformedTokenResponse = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/nao-e-um-uuid"), null, ApiError.class);
        assertThat(malformedTokenResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private AvailableSlotsResponse fetchAvailableSlots(Long doctorId, LocalDate date) {
        ResponseEntity<AvailableSlotsResponse> response = restTemplate.getForEntity(
                url("/api/public/doctors/" + doctorId + "/available-slots?date=" + date), AvailableSlotsResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private AppointmentResponse bookAppointment(Long doctorId, LocalDate date, LocalTime startTime, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, date, startTime, patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                url("/api/public/appointments"), request, AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void setAvailability(HttpHeaders headers, Long doctorId, int dayOfWeek, LocalTime start, LocalTime end) {
        List<AvailabilityRequest> windows = List.of(new AvailabilityRequest(dayOfWeek, start, end));
        ResponseEntity<List<AvailabilityResponse>> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(windows, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private DoctorResponse createDoctor(HttpHeaders headers, String name, String specialty) {
        DoctorCreateRequest request = new DoctorCreateRequest(name, specialty, null, null);
        ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private ClinicSession registerClinic(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AuthResponse body = registerResponse.getBody();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.token());
        return new ClinicSession(headers, body.clinic().slug(), body.clinic().id());
    }

    private record ClinicSession(HttpHeaders headers, String slug, Long clinicId) {
    }
}
