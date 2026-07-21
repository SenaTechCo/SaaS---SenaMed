package com.senamed.backend.appointment;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
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
 * Covers the authenticated "dashboard de consultas" listing (RF-018, part of Fase 4/KAN-64).
 */
class AppointmentDashboardIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();

    @Test
    void listAppointments_withoutToken_returns401() {
        ResponseEntity<String> response = restTemplate.getForEntity(url("/api/appointments"), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void listAppointments_returnsOnlyOwnClinicAppointments_includingCancelled() {
        ClinicSession clinicA = registerClinic("Clinica Dashboard A", "adminA@dashboard.com");
        ClinicSession clinicB = registerClinic("Clinica Dashboard B", "adminB@dashboard.com");

        Long doctorA = createDoctor(clinicA.headers, "Dr. Dashboard A", "Clinico Geral").id();
        setAvailability(clinicA.headers, doctorA, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(11, 0));
        Long doctorB = createDoctor(clinicB.headers, "Dr. Dashboard B", "Clinico Geral").id();
        setAvailability(clinicB.headers, doctorB, FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(11, 0));

        AppointmentResponse confirmed = bookAppointment(doctorA, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Confirmado", "confirmado@dashboard.com");
        AppointmentResponse toCancel = bookAppointment(doctorA, FUTURE_DATE, LocalTime.of(10, 0), "Paciente Cancelado", "cancelado@dashboard.com");
        bookAppointment(doctorB, FUTURE_DATE, LocalTime.of(9, 0), "Paciente Outra Clinica", "outraclinica@dashboard.com");

        restTemplate.postForEntity(
                url("/api/public/appointments/cancel/" + toCancel.cancelToken()), null, AppointmentResponse.class);

        ResponseEntity<List<AppointmentResponse>> response = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.GET, new HttpEntity<>(clinicA.headers),
                new ParameterizedTypeReference<List<AppointmentResponse>>() {
                });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        List<AppointmentResponse> body = response.getBody();
        assertThat(body).extracting(AppointmentResponse::patientName)
                .containsExactlyInAnyOrder("Paciente Confirmado", "Paciente Cancelado");
        assertThat(body).extracting(AppointmentResponse::status)
                .containsExactlyInAnyOrder(AppointmentStatus.CONFIRMED, AppointmentStatus.CANCELLED);
        assertThat(body).extracting(AppointmentResponse::id).containsExactlyInAnyOrder(confirmed.id(), toCancel.id());
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
