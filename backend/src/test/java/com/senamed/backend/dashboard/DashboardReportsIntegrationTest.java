package com.senamed.backend.dashboard;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.dto.ServiceItemRequest;
import com.senamed.backend.auth.dto.AuthClinicDto;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.dashboard.dto.DashboardReportsResponse;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import com.senamed.backend.finance.dto.CommissionConfigRequest;
import com.senamed.backend.finance.dto.CommissionConfigResponse;
import com.senamed.backend.finance.dto.ReceivableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers KAN-102: the clinic reports screen's daily-granularity time series and period totals
 * (gross revenue, direct cost, gross profit). Mirrors {@code AppointmentStaffManagementIntegrationTest}'s
 * conventions; unlike the other appointment tests, this suite backdates {@code starts_at}/
 * {@code created_at}/{@code paid_at} directly through {@link JdbcTemplate} (same technique as
 * {@code AppointmentFlowIntegrationTest#cancelAppointment_within24Hours_returns409}) after driving
 * the real create/atender/pagar/cancel/faltou endpoints, since those endpoints only accept
 * present/future dates but the reports feature needs data spread across the trailing days.
 */
class DashboardReportsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

    @Test
    void reports_aggregatesDailySeriesAndTotals_isolatedFromOtherClinics() {
        ClinicSession clinicA = registerClinic("Clinica Relatorios A", "admin@relatoriosa.com");
        ZoneId zoneA = ZoneId.of(clinicA.timezone);
        LocalDate today = LocalDate.now(zoneA);
        LocalDate day1 = today.minusDays(2);
        LocalDate day2 = today.minusDays(5);

        Long doctorA = createDoctor(clinicA.headers, "Dr. Relatorios A", "Clinico Geral").id();
        setCommission(clinicA.headers, doctorA, "20.00");
        ServiceOfferingResponse serviceA = createServiceOffering(clinicA.headers, "Consulta A", "200.00");
        ServiceOfferingResponse serviceB = createServiceOffering(clinicA.headers, "Consulta B", "100.00");

        // day1: one attended+paid appointment (200.00) and one cancelled appointment.
        AppointmentResponse appointment1 = createAppointment(clinicA.headers, doctorA, serviceA.id(), "Paciente Um", "um@relatoriosa.com");
        backdateAppointment(appointment1.id(), day1.atTime(9, 0));
        markAttended(clinicA.headers, appointment1.id());
        Long receivable1 = findReceivableIdByAppointment(clinicA.headers, appointment1.id());
        backdateReceivableCreatedAt(receivable1, day1.atTime(9, 30).atZone(zoneA).toInstant());
        markPaid(clinicA.headers, receivable1);
        backdateReceivablePaidAt(receivable1, day1.atTime(10, 0).atZone(zoneA).toInstant());

        AppointmentResponse appointment2 = createAppointment(clinicA.headers, doctorA, null, "Paciente Dois", "dois@relatoriosa.com");
        backdateAppointment(appointment2.id(), day1.atTime(11, 0));
        cancel(clinicA.headers, appointment2.id());

        // day2: one attended+unpaid appointment (100.00) and one no-show appointment.
        AppointmentResponse appointment3 = createAppointment(clinicA.headers, doctorA, serviceB.id(), "Paciente Tres", "tres@relatoriosa.com");
        backdateAppointment(appointment3.id(), day2.atTime(9, 0));
        markAttended(clinicA.headers, appointment3.id());
        Long receivable3 = findReceivableIdByAppointment(clinicA.headers, appointment3.id());
        backdateReceivableCreatedAt(receivable3, day2.atTime(9, 30).atZone(zoneA).toInstant());

        AppointmentResponse appointment4 = createAppointment(clinicA.headers, doctorA, null, "Paciente Quatro", "quatro@relatoriosa.com");
        backdateAppointment(appointment4.id(), day2.atTime(11, 0));
        markNoShow(clinicA.headers, appointment4.id());

        // A second clinic with its own attended+paid data in the same date range must not leak in.
        ClinicSession clinicB = registerClinic("Clinica Relatorios B", "admin@relatoriosb.com");
        ZoneId zoneB = ZoneId.of(clinicB.timezone);
        Long doctorB = createDoctor(clinicB.headers, "Dr. Relatorios B", "Clinico Geral").id();
        setCommission(clinicB.headers, doctorB, "50.00");
        ServiceOfferingResponse serviceC = createServiceOffering(clinicB.headers, "Consulta C", "999.00");
        AppointmentResponse appointmentB = createAppointment(clinicB.headers, doctorB, serviceC.id(), "Paciente B", "b@relatoriosb.com");
        backdateAppointment(appointmentB.id(), day1.atTime(9, 0));
        markAttended(clinicB.headers, appointmentB.id());
        Long receivableB = findReceivableIdByAppointment(clinicB.headers, appointmentB.id());
        backdateReceivableCreatedAt(receivableB, day1.atTime(9, 30).atZone(zoneB).toInstant());
        markPaid(clinicB.headers, receivableB);
        backdateReceivablePaidAt(receivableB, day1.atTime(10, 0).atZone(zoneB).toInstant());

        DashboardReportsResponse reportA = getReports(clinicA.headers, 14);

        assertThat(reportA.attendedCount()).isEqualTo(2);
        assertThat(reportA.cancelledCount()).isEqualTo(1);
        assertThat(reportA.noShowCount()).isEqualTo(1);
        assertThat(reportA.grossRevenue()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(reportA.directCost()).isEqualByComparingTo(new BigDecimal("60.00"));
        assertThat(reportA.grossProfit()).isEqualByComparingTo(new BigDecimal("140.00"));
        assertThat(reportA.dailySeries()).hasSize(14);

        DashboardReportsResponse.DailyPoint day1Point = pointFor(reportA, day1);
        assertThat(day1Point.attended()).isEqualTo(1);
        assertThat(day1Point.cancelled()).isEqualTo(1);
        assertThat(day1Point.noShow()).isEqualTo(0);
        assertThat(day1Point.received()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(day1Point.receivable()).isEqualByComparingTo(new BigDecimal("200.00"));

        DashboardReportsResponse.DailyPoint day2Point = pointFor(reportA, day2);
        assertThat(day2Point.attended()).isEqualTo(1);
        assertThat(day2Point.cancelled()).isEqualTo(0);
        assertThat(day2Point.noShow()).isEqualTo(1);
        assertThat(day2Point.received()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(day2Point.receivable()).isEqualByComparingTo(new BigDecimal("100.00"));

        // Clinic B's own report reflects only its own data, confirming isolation is bidirectional.
        DashboardReportsResponse reportB = getReports(clinicB.headers, 14);
        assertThat(reportB.attendedCount()).isEqualTo(1);
        assertThat(reportB.grossRevenue()).isEqualByComparingTo(new BigDecimal("999.00"));
        assertThat(reportB.directCost()).isEqualByComparingTo(new BigDecimal("499.50"));
    }

    @Test
    void reports_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/dashboard/reports"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void reports_asDoctorRole_returns403() {
        ClinicSession clinic = registerClinic("Clinica Relatorios Restricao", "admin@relatoriosrestricao.com");
        Long doctorId = createDoctor(clinic.headers, "Dr. Restrito", "Clinico Geral").id();

        DoctorAccessResponse access = grantAccess(clinic.headers, doctorId, "doutor@relatoriosrestricao.com", "SenhaForte123");
        assertThat(access).isNotNull();
        HttpHeaders doctorHeaders = loginHeaders("doutor@relatoriosrestricao.com", "SenhaForte123");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/dashboard/reports"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private DashboardReportsResponse.DailyPoint pointFor(DashboardReportsResponse report, LocalDate date) {
        return report.dailySeries().stream()
                .filter(point -> point.date().isEqual(date))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No daily point found for " + date));
    }

    private DashboardReportsResponse getReports(HttpHeaders headers, int days) {
        ResponseEntity<DashboardReportsResponse> response = restTemplate.exchange(
                url("/api/dashboard/reports?days=" + days), HttpMethod.GET,
                new HttpEntity<>(headers), DashboardReportsResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void backdateAppointment(Long appointmentId, LocalDateTime startsAt) {
        jdbcTemplate.update(
                "UPDATE appointments SET starts_at = ?, ends_at = ? WHERE id = ?",
                Timestamp.valueOf(startsAt), Timestamp.valueOf(startsAt.plusMinutes(30)), appointmentId);
    }

    private void backdateReceivableCreatedAt(Long receivableId, Instant createdAt) {
        jdbcTemplate.update("UPDATE receivables SET created_at = ? WHERE id = ?", Timestamp.from(createdAt), receivableId);
    }

    private void backdateReceivablePaidAt(Long receivableId, Instant paidAt) {
        jdbcTemplate.update("UPDATE receivables SET paid_at = ? WHERE id = ?", Timestamp.from(paidAt), receivableId);
    }

    private Long findReceivableIdByAppointment(HttpHeaders headers, Long appointmentId) {
        List<ReceivableResponse> receivables = listReceivables(headers);
        return receivables.stream()
                .filter(r -> r.appointmentId().equals(appointmentId))
                .findFirst()
                .orElseThrow(() -> new AssertionError("No receivable found for appointment " + appointmentId))
                .id();
    }

    private List<ReceivableResponse> listReceivables(HttpHeaders headers) {
        ResponseEntity<List<ReceivableResponse>> response = restTemplate.exchange(
                url("/api/finance/receivables"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ReceivableResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void markAttended(HttpHeaders headers, Long appointmentId) {
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointmentId + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void markNoShow(HttpHeaders headers, Long appointmentId) {
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointmentId + "/faltou"), HttpMethod.PATCH,
                new HttpEntity<>(headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void cancel(HttpHeaders headers, Long appointmentId) {
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointmentId + "/cancel"), HttpMethod.POST,
                new HttpEntity<>(headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void markPaid(HttpHeaders headers, Long receivableId) {
        ResponseEntity<ReceivableResponse> response = restTemplate.exchange(
                url("/api/finance/receivables/" + receivableId + "/pagar"), HttpMethod.PATCH,
                new HttpEntity<>(headers), ReceivableResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private void setCommission(HttpHeaders headers, Long doctorId, String percentage) {
        ResponseEntity<CommissionConfigResponse> response = restTemplate.exchange(
                url("/api/finance/commissions/" + doctorId), HttpMethod.PUT,
                new HttpEntity<>(new CommissionConfigRequest(new BigDecimal(percentage)), headers), CommissionConfigResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private AppointmentResponse createAppointment(HttpHeaders headers, Long doctorId, Long serviceId, String patientName, String patientEmail) {
        List<ServiceItemRequest> services = serviceId != null ? List.of(new ServiceItemRequest(serviceId, 1)) : List.of();
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, services, FUTURE_DATE, LocalTime.of(9, 0), patientName, patientEmail, "11999998888", true);
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

    private DoctorResponse createDoctor(HttpHeaders headers, String name, String specialty) {
        DoctorCreateRequest request = new DoctorCreateRequest(name, specialty, null, null);
        ResponseEntity<DoctorResponse> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST, new HttpEntity<>(request, headers), DoctorResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private DoctorAccessResponse grantAccess(HttpHeaders adminHeaders, Long doctorId, String email, String password) {
        ResponseEntity<DoctorAccessResponse> response = restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/access"), HttpMethod.POST,
                new HttpEntity<>(new GrantDoctorAccessRequest(email, password, null), adminHeaders), DoctorAccessResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpHeaders loginHeaders(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest(email, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(response.getBody().token());
        return headers;
    }

    private ClinicSession registerClinic(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        AuthResponse body = registerResponse.getBody();
        AuthClinicDto clinic = body.clinic();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(body.token());
        return new ClinicSession(headers, clinic.id(), clinic.timezone());
    }

    private record ClinicSession(HttpHeaders headers, Long clinicId, String timezone) {
    }
}
