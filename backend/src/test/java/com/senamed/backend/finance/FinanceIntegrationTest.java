package com.senamed.backend.finance;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.dto.ServiceItemRequest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.finance.dto.CommissionCalculationResponse;
import com.senamed.backend.finance.dto.CommissionConfigRequest;
import com.senamed.backend.finance.dto.CommissionConfigResponse;
import com.senamed.backend.finance.dto.ReceivableResponse;
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
 * Covers KAN-100/Financeiro: auto-billing a "Conta a Receber" when a booked appointment (with a
 * linked service) is marked as attended, paying it off, and computing a doctor's monthly
 * commission - mirrors {@code AppointmentStaffManagementIntegrationTest}'s conventions.
 */
class FinanceIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);

    @Test
    void markAttended_withService_autoCreatesPendingReceivable() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Financeiro Um", "admin@financeiro1.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Financeiro", "Clinico Geral").id();
        ServiceOfferingResponse service = createServiceOffering(adminHeaders, "Consulta Financeiro", "200.00");

        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, service.id(), "Paciente Financeiro", "paciente@financeiro1.com");
        markAttended(adminHeaders, appointment.id());

        List<ReceivableResponse> receivables = listReceivables(adminHeaders, null);
        assertThat(receivables).hasSize(1);
        ReceivableResponse receivable = receivables.get(0);
        assertThat(receivable.appointmentId()).isEqualTo(appointment.id());
        assertThat(receivable.description()).isEqualTo("Consulta Financeiro");
        assertThat(receivable.amount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(receivable.status()).isEqualTo(ReceivableStatus.PENDING);
        assertThat(receivable.paidAt()).isNull();
        assertThat(receivable.patientName()).isEqualTo("Paciente Financeiro");
    }

    @Test
    void markAttended_withoutService_doesNotCreateReceivable() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Financeiro Sem Servico", "admin@financeirosemservico.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Sem Servico", "Clinico Geral").id();

        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, null, "Paciente Sem Servico", "paciente@financeirosemservico.com");
        markAttended(adminHeaders, appointment.id());

        assertThat(listReceivables(adminHeaders, null)).isEmpty();
    }

    @Test
    void markPaid_movesReceivableToPaid_andSetsPaidAt() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Financeiro Pagar", "admin@financeiropagar.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Pagar", "Clinico Geral").id();
        ServiceOfferingResponse service = createServiceOffering(adminHeaders, "Consulta Pagar", "100.00");
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, service.id(), "Paciente Pagar", "paciente@financeiropagar.com");
        markAttended(adminHeaders, appointment.id());
        Long receivableId = listReceivables(adminHeaders, null).get(0).id();

        ResponseEntity<ReceivableResponse> response = restTemplate.exchange(
                url("/api/finance/receivables/" + receivableId + "/pagar"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ReceivableResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().status()).isEqualTo(ReceivableStatus.PAID);
        assertThat(response.getBody().paidAt()).isNotNull();
    }

    @Test
    void markPaid_alreadyPaidReceivable_returns400() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Financeiro Pagar Duplo", "admin@financeiropagarduplo.com");
        Long doctorId = createDoctor(adminHeaders, "Dr. Pagar Duplo", "Clinico Geral").id();
        ServiceOfferingResponse service = createServiceOffering(adminHeaders, "Consulta Pagar Duplo", "100.00");
        AppointmentResponse appointment = createAppointment(
                adminHeaders, doctorId, service.id(), "Paciente Pagar Duplo", "paciente@financeiropagarduplo.com");
        markAttended(adminHeaders, appointment.id());
        Long receivableId = listReceivables(adminHeaders, null).get(0).id();

        restTemplate.exchange(
                url("/api/finance/receivables/" + receivableId + "/pagar"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ReceivableResponse.class);

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/finance/receivables/" + receivableId + "/pagar"), HttpMethod.PATCH,
                new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void commission_upsertConfigThenCalculate_returnsSumTimesPercentage() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Comissao", "admin@comissao.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Comissao", "Clinico Geral");
        ServiceOfferingResponse serviceA = createServiceOffering(adminHeaders, "Consulta A", "100.00");
        ServiceOfferingResponse serviceB = createServiceOffering(adminHeaders, "Consulta B", "300.00");

        ResponseEntity<CommissionConfigResponse> upsertResponse = restTemplate.exchange(
                url("/api/finance/commissions/" + doctor.id()), HttpMethod.PUT,
                new HttpEntity<>(new CommissionConfigRequest(new BigDecimal("20.00")), adminHeaders), CommissionConfigResponse.class);
        assertThat(upsertResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(upsertResponse.getBody().percentage()).isEqualByComparingTo(new BigDecimal("20.00"));

        AppointmentResponse appointmentA = createAppointment(
                adminHeaders, doctor.id(), serviceA.id(), LocalTime.of(9, 0), "Paciente Comissao A", "pacientea@comissao.com");
        AppointmentResponse appointmentB = createAppointment(
                adminHeaders, doctor.id(), serviceB.id(), LocalTime.of(10, 0), "Paciente Comissao B", "pacienteb@comissao.com");
        markAttended(adminHeaders, appointmentA.id());
        markAttended(adminHeaders, appointmentB.id());

        LocalDate today = LocalDate.now();
        ResponseEntity<CommissionCalculationResponse> calculateResponse = restTemplate.exchange(
                url("/api/finance/commissions/" + doctor.id() + "?year=" + today.getYear() + "&month=" + today.getMonthValue()),
                HttpMethod.GET, new HttpEntity<>(adminHeaders), CommissionCalculationResponse.class);

        assertThat(calculateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        CommissionCalculationResponse body = calculateResponse.getBody();
        assertThat(body.totalBilled()).isEqualByComparingTo(new BigDecimal("400.00"));
        assertThat(body.percentage()).isEqualByComparingTo(new BigDecimal("20.00"));
        assertThat(body.commissionAmount()).isEqualByComparingTo(new BigDecimal("80.00"));
    }

    @Test
    void commission_getConfig_withoutUpsert_returnsUnconfiguredDefault() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica Comissao Nao Configurada", "admin@comissaonaoconfigurada.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Sem Comissao", "Clinico Geral");

        ResponseEntity<CommissionConfigResponse> response = restTemplate.exchange(
                url("/api/finance/commissions/" + doctor.id() + "/config"), HttpMethod.GET,
                new HttpEntity<>(adminHeaders), CommissionConfigResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().active()).isFalse();
        assertThat(response.getBody().percentage()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void finance_isIsolatedBetweenClinics() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Financeiro A", "adminA@financeiroiso.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Financeiro B", "adminB@financeiroiso.com");

        Long doctorA = createDoctor(clinicAHeaders, "Dr. Iso A", "Clinico Geral").id();
        ServiceOfferingResponse serviceA = createServiceOffering(clinicAHeaders, "Consulta Iso A", "150.00");
        AppointmentResponse appointmentA = createAppointment(
                clinicAHeaders, doctorA, serviceA.id(), "Paciente Iso A", "pacientea@financeiroiso.com");
        markAttended(clinicAHeaders, appointmentA.id());
        Long receivableIdA = listReceivables(clinicAHeaders, null).get(0).id();

        restTemplate.exchange(
                url("/api/finance/commissions/" + doctorA), HttpMethod.PUT,
                new HttpEntity<>(new CommissionConfigRequest(new BigDecimal("10.00")), clinicAHeaders), CommissionConfigResponse.class);

        // Clinic B sees no receivables at all - the one above belongs to clinic A.
        assertThat(listReceivables(clinicBHeaders, null)).isEmpty();

        // Clinic B cannot pay off clinic A's receivable.
        ResponseEntity<ApiError> payResponse = restTemplate.exchange(
                url("/api/finance/receivables/" + receivableIdA + "/pagar"), HttpMethod.PATCH,
                new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(payResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        // Clinic B has no visibility into clinic A doctor's commission config - it's unconfigured from B's perspective.
        ResponseEntity<CommissionConfigResponse> configFromB = restTemplate.exchange(
                url("/api/finance/commissions/" + doctorA + "/config"), HttpMethod.GET,
                new HttpEntity<>(clinicBHeaders), CommissionConfigResponse.class);
        assertThat(configFromB.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(configFromB.getBody().active()).isFalse();
    }

    private void markAttended(HttpHeaders headers, Long appointmentId) {
        ResponseEntity<AppointmentResponse> response = restTemplate.exchange(
                url("/api/appointments/" + appointmentId + "/atender"), HttpMethod.PATCH,
                new HttpEntity<>(headers), AppointmentResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    private List<ReceivableResponse> listReceivables(HttpHeaders headers, ReceivableStatus status) {
        String query = status != null ? "?status=" + status : "";
        ResponseEntity<List<ReceivableResponse>> response = restTemplate.exchange(
                url("/api/finance/receivables" + query), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ReceivableResponse>>() {
                });
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private AppointmentResponse createAppointment(
            HttpHeaders headers, Long doctorId, Long serviceId, String patientName, String patientEmail) {
        return createAppointment(headers, doctorId, serviceId, LocalTime.of(9, 0), patientName, patientEmail);
    }

    private AppointmentResponse createAppointment(
            HttpHeaders headers, Long doctorId, Long serviceId, LocalTime startTime, String patientName, String patientEmail) {
        List<ServiceItemRequest> services = serviceId != null ? List.of(new ServiceItemRequest(serviceId, 1)) : List.of();
        AppointmentCreateRequest request = new AppointmentCreateRequest(
                doctorId, null, services, FUTURE_DATE, startTime, patientName, patientEmail, "11999998888", true);
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
