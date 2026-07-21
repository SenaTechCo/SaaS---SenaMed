package com.senamed.backend.googlecalendar;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.doctor.Doctor;
import com.senamed.backend.doctor.DoctorRepository;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Covers KAN-78: the appointment_calendar_sync_jobs outbox, its listener and its processor. */
class AppointmentCalendarSyncIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Autowired
    private DoctorRepository doctorRepository;

    @Autowired
    private DoctorGoogleCalendarCredentialRepository credentialRepository;

    @Autowired
    private AppointmentCalendarSyncJobRepository syncJobRepository;

    @Autowired
    private AppointmentCalendarSyncProcessor processor;

    @MockitoBean
    private GoogleCalendarClient googleCalendarClient;

    @Test
    void booking_withoutConnectedDoctor_createsNoSyncJob() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sync Sem Conexao", "admin@syncsemconexao.com");
        Long doctorId = setUpAvailableDoctor(adminHeaders, "Dr. Sem Conexao");

        bookAppointment(doctorId, "Paciente Sem Conexao", "paciente@syncsemconexao.com");

        assertThat(syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING)).isEmpty();
    }

    @Test
    void booking_withConnectedDoctor_createsPendingCreateJob_thenProcessorSendsIt() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sync Com Conexao", "admin@synccomconexao.com");
        Long doctorId = setUpAvailableDoctor(adminHeaders, "Dr. Com Conexao");
        connectGoogleCalendar(doctorId, "doutor@synccomconexao.com");

        when(googleCalendarClient.createEvent(anyString(), any())).thenReturn("google-event-1");
        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Com Conexao", "paciente@synccomconexao.com");

        List<AppointmentCalendarSyncJob> pending = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING);
        assertThat(pending).hasSize(1);
        AppointmentCalendarSyncJob job = pending.get(0);
        assertThat(job.getType()).isEqualTo(SyncJobType.CREATE_EVENT);
        assertThat(job.getAppointment().getId()).isEqualTo(appointment.id());

        processor.processOne(job.getId());

        AppointmentCalendarSyncJob processed = syncJobRepository.findById(job.getId()).orElseThrow();
        assertThat(processed.getStatus()).isEqualTo(SyncJobStatus.SENT);
        assertThat(processed.getGoogleEventId()).isEqualTo("google-event-1");
    }

    @Test
    void cancelAfterCreateEventSent_createsCancelJobThatDeletesTheRightEvent() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sync Cancelar Depois", "admin@synccancelardepois.com");
        Long doctorId = setUpAvailableDoctor(adminHeaders, "Dr. Cancelar Depois");
        connectGoogleCalendar(doctorId, "doutor@synccancelardepois.com");

        when(googleCalendarClient.createEvent(anyString(), any())).thenReturn("google-event-2");
        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Cancelar Depois", "paciente@synccancelardepois.com");
        AppointmentCalendarSyncJob createJob = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING).get(0);
        processor.processOne(createJob.getId()); // CREATE_EVENT -> SENT before cancelling

        cancelAppointment(appointment.cancelToken());

        List<AppointmentCalendarSyncJob> pendingAfterCancel = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING);
        assertThat(pendingAfterCancel).hasSize(1);
        AppointmentCalendarSyncJob cancelJob = pendingAfterCancel.get(0);
        assertThat(cancelJob.getType()).isEqualTo(SyncJobType.CANCEL_EVENT);

        processor.processOne(cancelJob.getId());

        verify(googleCalendarClient, times(1)).deleteEvent(anyString(), org.mockito.ArgumentMatchers.eq("google-event-2"));
        assertThat(syncJobRepository.findById(cancelJob.getId()).orElseThrow().getStatus()).isEqualTo(SyncJobStatus.SENT);
    }

    @Test
    void cancelBeforeCreateEventProcessed_skipsTheCreateJob_neverCallsGoogle() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sync Cancelar Antes", "admin@synccancelarantes.com");
        Long doctorId = setUpAvailableDoctor(adminHeaders, "Dr. Cancelar Antes");
        connectGoogleCalendar(doctorId, "doutor@synccancelarantes.com");

        AppointmentResponse appointment = bookAppointment(doctorId, "Paciente Cancelar Antes", "paciente@synccancelarantes.com");
        cancelAppointment(appointment.cancelToken()); // cancel before the CREATE_EVENT job ever runs

        AppointmentCalendarSyncJob createJob = syncJobRepository.findAll().stream()
                .filter(j -> j.getAppointment().getId().equals(appointment.id()))
                .findFirst().orElseThrow();
        assertThat(createJob.getStatus()).isEqualTo(SyncJobStatus.SKIPPED);
        assertThat(syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING)).isEmpty();

        verify(googleCalendarClient, never()).createEvent(anyString(), any());
        verify(googleCalendarClient, never()).deleteEvent(anyString(), anyString());
    }

    @Test
    void failedProcessing_incrementsAttempts_andStaysPendingUntilMaxAttempts() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Sync Falha", "admin@syncfalha.com");
        Long doctorId = setUpAvailableDoctor(adminHeaders, "Dr. Falha");
        connectGoogleCalendar(doctorId, "doutor@syncfalha.com");

        when(googleCalendarClient.createEvent(anyString(), any())).thenThrow(new GoogleCalendarIntegrationException("boom", null));
        bookAppointment(doctorId, "Paciente Falha", "paciente@syncfalha.com");
        AppointmentCalendarSyncJob job = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING).get(0);

        for (int i = 1; i <= 4; i++) {
            processor.processOne(job.getId());
            AppointmentCalendarSyncJob afterAttempt = syncJobRepository.findById(job.getId()).orElseThrow();
            assertThat(afterAttempt.getStatus()).isEqualTo(SyncJobStatus.PENDING);
            assertThat(afterAttempt.getAttempts()).isEqualTo(i);
        }

        processor.processOne(job.getId()); // 5th attempt -> FAILED
        AppointmentCalendarSyncJob finalState = syncJobRepository.findById(job.getId()).orElseThrow();
        assertThat(finalState.getStatus()).isEqualTo(SyncJobStatus.FAILED);
        assertThat(finalState.getAttempts()).isEqualTo(5);
    }

    private void connectGoogleCalendar(Long doctorId, String googleEmail) {
        Doctor doctor = doctorRepository.findById(doctorId).orElseThrow();
        credentialRepository.save(new DoctorGoogleCalendarCredential(doctor, googleEmail, "refresh-token-test"));
    }

    private Long setUpAvailableDoctor(HttpHeaders adminHeaders, String name) {
        DoctorResponse doctor = createDoctor(adminHeaders, name, "Clinico Geral");
        setAvailability(adminHeaders, doctor.id(), FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(11, 0));
        return doctor.id();
    }

    private AppointmentResponse bookAppointment(Long doctorId, String patientName, String patientEmail) {
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, List.of(), FUTURE_DATE, LocalTime.of(9, 0), patientName, patientEmail, "11999998888", true);
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                url("/api/public/appointments"), request, AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private void cancelAppointment(String cancelToken) {
        ResponseEntity<AppointmentResponse> response = restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + cancelToken), null, AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void setAvailability(HttpHeaders headers, Long doctorId, int dayOfWeek, LocalTime start, LocalTime end) {
        List<AvailabilityRequest> windows = List.of(new AvailabilityRequest(dayOfWeek, start, end));
        ResponseEntity<List<AvailabilityResponse>> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST, new HttpEntity<>(windows, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() { });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
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
