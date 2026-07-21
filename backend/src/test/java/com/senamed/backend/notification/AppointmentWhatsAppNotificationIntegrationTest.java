package com.senamed.backend.notification;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.notification.whatsapp.WhatsAppClient;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/** Covers KAN-79: WhatsApp as a second appointment_messages channel alongside e-mail. */
class AppointmentWhatsAppNotificationIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private AppointmentMessageScheduler scheduler;

    @MockitoBean
    private WhatsAppClient whatsAppClient;

    @Test
    void bookingWithPhone_createsBothEmailAndWhatsAppMessages() {
        ClinicSession clinic = registerClinic("Clinica WhatsApp Com Telefone", "admin@whatsappcomtelefone.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. WhatsApp Com Telefone", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente WhatsApp", "whatsapp@paciente.com", "11999998888");

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT type, channel FROM appointment_messages WHERE appointment_id = ? ORDER BY channel, type",
                appointment.id());
        assertThat(rows).hasSize(4);
        assertThat(rows).extracting(r -> r.get("channel")).containsOnly("EMAIL", "WHATSAPP");

        Integer whatsAppCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM appointment_messages WHERE appointment_id = ? AND channel = 'WHATSAPP'",
                Integer.class, appointment.id());
        assertThat(whatsAppCount).isEqualTo(2);
    }

    @Test
    void bookingWithoutPhone_createsOnlyEmailMessages() {
        ClinicSession clinic = registerClinic("Clinica WhatsApp Sem Telefone", "admin@whatsappsemtelefone.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. WhatsApp Sem Telefone", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));

        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Sem Telefone", "semtelefone@paciente.com", null);

        List<String> channels = jdbcTemplate.queryForList(
                "SELECT channel FROM appointment_messages WHERE appointment_id = ?", String.class, appointment.id());
        assertThat(channels).containsOnly("EMAIL");
        assertThat(channels).hasSize(2);
    }

    @Test
    void scheduler_sendsDueWhatsAppMessage_marksSent() {
        ClinicSession clinic = registerClinic("Clinica WhatsApp Scheduler Ok", "admin@whatsappschedulerok.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. WhatsApp Scheduler Ok", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Scheduler", "schedulerwa@paciente.com", "11988887777");

        // CREATED_CONFIRMATION is scheduled_for = now() at creation time, so it's already due.
        scheduler.processDueMessages();

        verify(whatsAppClient, times(1)).sendTemplateMessage(anyString(), eq("agendamento_confirmado"), anyList());
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM appointment_messages WHERE appointment_id = ? AND type = 'CREATED_CONFIRMATION' AND channel = 'WHATSAPP'",
                String.class, appointment.id());
        assertThat(status).isEqualTo("SENT");
    }

    @Test
    void scheduler_sendFailure_incrementsAttempts_thenFailsAfterMaxAttempts() {
        doThrow(new RuntimeException("WhatsApp indisponivel")).when(whatsAppClient)
                .sendTemplateMessage(anyString(), anyString(), anyList());

        ClinicSession clinic = registerClinic("Clinica WhatsApp Scheduler Falha", "admin@whatsappschedulerfalha.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. WhatsApp Scheduler Falha", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Falha", "falhawa@paciente.com", "11977776666");

        for (int i = 0; i < 5; i++) {
            scheduler.processDueMessages();
        }

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT status, attempts FROM appointment_messages WHERE appointment_id = ? AND type = 'CREATED_CONFIRMATION' AND channel = 'WHATSAPP'",
                appointment.id());
        assertThat(row.get("attempts")).isEqualTo(5);
        assertThat(row.get("status")).isEqualTo("FAILED");
    }

    @Test
    void cancellingAppointment_flipsPendingMessagesOfBothChannelsToCancelled() {
        ClinicSession clinic = registerClinic("Clinica WhatsApp Cancelar", "admin@whatsappcancelar.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. WhatsApp Cancelar", "Clinico Geral").id();
        setAvailability(clinic.headers, doctorId, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0));
        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Cancela WA", "cancelawa@paciente.com", "11966665555");

        restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + appointment.cancelToken()), null, AppointmentResponse.class);

        List<String> statuses = jdbcTemplate.queryForList(
                "SELECT status FROM appointment_messages WHERE appointment_id = ?", String.class, appointment.id());
        assertThat(statuses).hasSize(4).containsOnly("CANCELLED");
    }

    private AppointmentResponse bookAppointment(Long doctorId, String patientName, String patientEmail, String patientPhone) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, List.of(), FUTURE_DATE, LocalTime.of(9, 0), patientName, patientEmail, patientPhone, true);
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
