package com.senamed.backend.notification;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import com.senamed.backend.notification.whatsapp.WhatsAppClient;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Covers Fase 4 (KAN-60..64): the appointment_messages outbox created on booking/cancellation, the
 * public confirm-attendance endpoint, and the scheduler's send/retry behaviour.
 */
class AppointmentMessageFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppointmentMessageScheduler scheduler;

    @MockitoBean
    private JavaMailSender javaMailSender;

    /** Only present to guarantee no real network call happens - this file doesn't test WhatsApp behavior. */
    @MockitoBean
    private WhatsAppClient whatsAppClient;

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Test
    void bookingAppointment_createsConfirmationAndReminderMessages_bothPending() {
        ClinicSession clinic = registerClinic("Clinica Mensagens", "admin@mensagens.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Mensagens", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Msg", "msg@paciente.com");

        List<String> types = jdbcTemplate.queryForList(
                "SELECT type FROM appointment_messages WHERE appointment_id = ? AND channel = 'EMAIL' ORDER BY type",
                String.class, appointment.id());
        assertThat(types).containsExactly("CREATED_CONFIRMATION", "REMINDER_24H");

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM appointment_messages WHERE appointment_id = ? AND channel = 'EMAIL'",
                String.class, appointment.id());
        assertThat(statuses).containsOnly("PENDING");

        // Compared against appointments.starts_at read the same raw-JDBC way (rather than the
        // Java-computed FUTURE_DATE/LocalTime literals) because hibernate.jdbc.time_zone=UTC makes
        // Hibernate-mediated reads/writes apply a compensating shift that a raw JDBC read bypasses -
        // consistent within one path, but not comparable across the two.
        LocalDateTime startsAt = jdbcTemplate.queryForObject(
                "SELECT starts_at FROM appointments WHERE id = ?", LocalDateTime.class, appointment.id());
        LocalDateTime reminderScheduledFor = jdbcTemplate.queryForObject(
                "SELECT scheduled_for FROM appointment_messages WHERE appointment_id = ? AND type = 'REMINDER_24H' AND channel = 'EMAIL'",
                LocalDateTime.class, appointment.id());
        LocalDateTime reminderTokenExpiresAt = jdbcTemplate.queryForObject(
                "SELECT token_expires_at FROM appointment_messages WHERE appointment_id = ? AND type = 'REMINDER_24H' AND channel = 'EMAIL'",
                LocalDateTime.class, appointment.id());

        assertThat(reminderScheduledFor).isEqualTo(startsAt.minusHours(24));
        assertThat(reminderTokenExpiresAt).isEqualTo(startsAt);
    }

    @Test
    void cancellingAppointment_flipsPendingMessagesToCancelled() {
        ClinicSession clinic = registerClinic("Clinica Mensagens Cancel", "admin@mensagenscancel.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Mensagens Cancel", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Cancela Msg", "cancelamsg@paciente.com");

        restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + appointment.cancelToken()), null, AppointmentResponse.class);

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM appointment_messages WHERE appointment_id = ? AND channel = 'EMAIL'",
                String.class, appointment.id());
        assertThat(statuses).containsOnly("CANCELLED");
    }

    @Test
    void confirmAttendance_happyPath_thenAlreadyConfirmed_returns409() {
        ClinicSession clinic = registerClinic("Clinica Confirmacao", "admin@confirmacao.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Confirmacao", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Confirma", "confirma@paciente.com");

        UUID confirmationToken = reminderTokenFor(appointment.id());

        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/" + confirmationToken), null, AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().confirmedAt()).isNotNull();

        ResponseEntity<ApiError> secondResponse = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/" + confirmationToken), null, ApiError.class);
        assertThat(secondResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(secondResponse.getBody().message()).contains("já confirmada");
    }

    @Test
    void confirmAttendance_cancelledAppointment_returns409() {
        ClinicSession clinic = registerClinic("Clinica Confirma Cancelado", "admin@confirmacancelado.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Confirma Cancelado", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Confirma Cancelado", "confirmacancelado@paciente.com");

        UUID confirmationToken = reminderTokenFor(appointment.id());
        restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + appointment.cancelToken()), null, AppointmentResponse.class);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/" + confirmationToken), null, ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("cancelado");
    }

    @Test
    void confirmAttendance_expiredToken_returns409() {
        ClinicSession clinic = registerClinic("Clinica Token Expirado", "admin@tokenexpirado.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Token Expirado", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Token Expirado", "tokenexpirado@paciente.com");

        UUID confirmationToken = reminderTokenFor(appointment.id());
        jdbcTemplate.update(
                "UPDATE appointment_messages SET token_expires_at = ? WHERE confirmation_token = ?",
                java.sql.Timestamp.valueOf(LocalDateTime.now().minusHours(1)), confirmationToken);

        ResponseEntity<ApiError> response = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/" + confirmationToken), null, ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(response.getBody().message()).contains("expirado");
    }

    @Test
    void confirmAttendance_unknownOrMalformedToken_returns404() {
        ResponseEntity<ApiError> unknownResponse = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/" + UUID.randomUUID()), null, ApiError.class);
        assertThat(unknownResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> malformedResponse = restTemplate.postForEntity(
                url("/api/public/appointments/confirm/nao-e-um-uuid"), null, ApiError.class);
        assertThat(malformedResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void scheduler_sendsDueMessage_marksSent() {
        ClinicSession clinic = registerClinic("Clinica Scheduler Ok", "admin@schedulerok.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Scheduler Ok", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Scheduler", "scheduler@paciente.com");

        // CREATED_CONFIRMATION is scheduled_for = now() at creation time, so it's already due.
        scheduler.processDueMessages();

        verify(javaMailSender, times(1)).send(any(SimpleMailMessage.class));
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_messages WHERE appointment_id = ? AND type = 'CREATED_CONFIRMATION' AND channel = 'EMAIL'",
                String.class, appointment.id());
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void scheduler_sendFailure_incrementsAttempts_thenFailsAfterMaxAttempts() {
        doThrow(new RuntimeException("SMTP indisponivel")).when(javaMailSender).send(any(SimpleMailMessage.class));

        ClinicSession clinic = registerClinic("Clinica Scheduler Falha", "admin@schedulerfalha.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Scheduler Falha", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Scheduler Falha", "schedulerfalha@paciente.com");

        for (int i = 0; i < 5; i++) {
            scheduler.processDueMessages();
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, attempts FROM appointment_messages WHERE appointment_id = ? AND type = 'CREATED_CONFIRMATION' AND channel = 'EMAIL'",
                appointment.id());
        assertThat(row.get("attempts")).isEqualTo(5);
        assertThat(row.get("status")).isEqualTo("FAILED");
    }

    private UUID reminderTokenFor(Long appointmentId) {
        return jdbcTemplate.queryForObject(
                "SELECT confirmation_token FROM appointment_messages WHERE appointment_id = ? AND type = 'REMINDER_24H' AND channel = 'EMAIL'",
                UUID.class, appointmentId);
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
