package com.senamed.backend.googlecalendar;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import com.senamed.backend.googlecalendar.dto.ConnectUrlResponse;
import com.senamed.backend.googlecalendar.dto.GoogleCalendarStatusResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.net.http.HttpClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/** Covers KAN-78: a doctor connecting/disconnecting their own Google Calendar. */
class DoctorGoogleCalendarIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private GoogleOAuthStateService stateService;

    @MockitoBean
    private GoogleCalendarClient googleCalendarClient;

    /**
     * The callback endpoint 302-redirects to the frontend, which isn't running during tests -
     * {@code restTemplate} would otherwise follow that redirect and fail trying to actually
     * connect to it. This client never follows redirects, so tests can assert on the 302 itself.
     */
    private final TestRestTemplate noRedirectRestTemplate = new TestRestTemplate(new RestTemplateBuilder()
            .requestFactory(() -> new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.NEVER)
                    .build())));

    @Test
    void getStatus_notConnected_returnsDisconnected() {
        HttpHeaders doctorHeaders = registerClinicWithConnectedDoctorHeaders("Clinica GCal Status", "admin@gcalstatus.com");

        ResponseEntity<GoogleCalendarStatusResponse> response = restTemplate.exchange(
                url("/api/doctors/me/google-calendar"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), GoogleCalendarStatusResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().connected()).isFalse();
        assertThat(response.getBody().googleEmail()).isNull();
    }

    @Test
    void getConnectUrl_delegatesToClient() {
        when(googleCalendarClient.buildAuthorizationUrl(anyString()))
                .thenReturn("https://accounts.google.com/mock-consent");
        HttpHeaders doctorHeaders = registerClinicWithConnectedDoctorHeaders("Clinica GCal ConnectUrl", "admin@gcalconnecturl.com");

        ResponseEntity<ConnectUrlResponse> response = restTemplate.exchange(
                url("/api/doctors/me/google-calendar/connect-url"), HttpMethod.GET,
                new HttpEntity<>(doctorHeaders), ConnectUrlResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().authorizationUrl()).isEqualTo("https://accounts.google.com/mock-consent");
    }

    @Test
    void callback_validStateAndCode_connectsAndRedirects() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica GCal Callback", "admin@gcalcallback.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Callback", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor.callback@gcalcallback.com", "SenhaForte123");

        when(googleCalendarClient.exchangeAuthorizationCode("auth-code-1"))
                .thenReturn(new GoogleTokenExchangeResult("refresh-token-1", "doutor.callback@gmail.com"));

        String state = stateService.sign(doctor.id());
        ResponseEntity<Void> response = noRedirectRestTemplate.exchange(
                url("/api/public/google-calendar/callback?code=auth-code-1&state=" + state),
                HttpMethod.GET, HttpEntity.EMPTY, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).contains("status=connected");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT google_email FROM doctor_google_calendar_credentials WHERE doctor_id = ?", String.class, doctor.id()))
                .isEqualTo("doutor.callback@gmail.com");
    }

    @Test
    void callback_invalidState_redirectsWithErrorAndCreatesNothing() {
        ResponseEntity<Void> response = noRedirectRestTemplate.exchange(
                url("/api/public/google-calendar/callback?code=auth-code-x&state=garbage-state"),
                HttpMethod.GET, HttpEntity.EMPTY, Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FOUND);
        assertThat(response.getHeaders().getLocation().toString()).contains("status=error");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM doctor_google_calendar_credentials", Integer.class))
                .isZero();
    }

    @Test
    void disconnect_removesCredential() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica GCal Disconnect", "admin@gcaldisconnect.com");
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. Disconnect", "Clinico Geral");
        grantAccess(adminHeaders, doctor.id(), "doutor.disconnect@gcaldisconnect.com", "SenhaForte123");
        HttpHeaders doctorHeaders = loginHeaders("doutor.disconnect@gcaldisconnect.com", "SenhaForte123");

        when(googleCalendarClient.exchangeAuthorizationCode(any()))
                .thenReturn(new GoogleTokenExchangeResult("refresh-token-2", "doutor.disconnect@gmail.com"));
        noRedirectRestTemplate.exchange(
                url("/api/public/google-calendar/callback?code=auth-code-2&state=" + stateService.sign(doctor.id())),
                HttpMethod.GET, HttpEntity.EMPTY, Void.class);

        ResponseEntity<Void> disconnectResponse = restTemplate.exchange(
                url("/api/doctors/me/google-calendar"), HttpMethod.DELETE, new HttpEntity<>(doctorHeaders), Void.class);
        assertThat(disconnectResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<GoogleCalendarStatusResponse> statusResponse = restTemplate.exchange(
                url("/api/doctors/me/google-calendar"), HttpMethod.GET, new HttpEntity<>(doctorHeaders), GoogleCalendarStatusResponse.class);
        assertThat(statusResponse.getBody().connected()).isFalse();
    }

    @Test
    void disconnect_noExistingConnection_returns404() {
        HttpHeaders doctorHeaders = registerClinicWithConnectedDoctorHeaders("Clinica GCal Disconnect 404", "admin@gcaldisconnect404.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/me/google-calendar"), HttpMethod.DELETE, new HttpEntity<>(doctorHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void googleCalendarMeEndpoints_asAdminToken_return403() {
        HttpHeaders adminHeaders = authHeadersForNewClinic("Clinica GCal Admin Token", "admin@gcaladmintoken.com");

        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/doctors/me/google-calendar"), HttpMethod.GET, new HttpEntity<>(adminHeaders), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    private HttpHeaders registerClinicWithConnectedDoctorHeaders(String clinicName, String adminEmail) {
        HttpHeaders adminHeaders = authHeadersForNewClinic(clinicName, adminEmail);
        DoctorResponse doctor = createDoctor(adminHeaders, "Dr. " + clinicName, "Clinico Geral");
        String doctorEmail = "doutor." + adminEmail;
        grantAccess(adminHeaders, doctor.id(), doctorEmail, "SenhaForte123");
        return loginHeaders(doctorEmail, "SenhaForte123");
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
