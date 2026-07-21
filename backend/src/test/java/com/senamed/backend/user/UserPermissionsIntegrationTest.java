package com.senamed.backend.user;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.common.ApiError;
import com.senamed.backend.security.AuthenticatedUser;
import com.senamed.backend.security.JwtService;
import com.senamed.backend.user.dto.UserCreateRequest;
import com.senamed.backend.user.dto.UserManagementResponse;
import com.senamed.backend.user.dto.UserUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the granular per-user permission system (replacing the coarse ADMIN/DOCTOR-only
 * authorization model): ADMIN always has every permission, STAFF only has what was explicitly
 * granted, read-only doctor endpoints stay open to any authenticated user, and the clinic's ADMIN
 * account can never be edited/deleted through {@code /api/users}.
 */
class UserPermissionsIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private JwtService jwtService;

    @Test
    void adminToken_carriesAllPermissions_regardlessOfDbRows() {
        AuthResponse auth = registerClinic("Clinica Permissoes Admin", "admin@permissoesadmin.com");

        AuthenticatedUser principal = jwtService.parseToken(auth.token()).orElseThrow();

        assertThat(principal.permissions()).containsExactlyInAnyOrder(
                "MANAGE_PATIENTS", "MANAGE_APPOINTMENTS", "MANAGE_FINANCE",
                "MANAGE_SERVICES", "MANAGE_USERS", "VIEW_REPORTS");
    }

    @Test
    void staffWithOnlyAppointmentsPermission_canAccessAppointments_butForbiddenElsewhere() {
        HttpHeaders adminHeaders = headersFrom(registerClinic("Clinica Staff Restrito", "admin@staffrestrito.com"));

        UserManagementResponse staff = createStaff(
                adminHeaders, "Recepcao", "recepcao@staffrestrito.com", Set.of(Permission.MANAGE_APPOINTMENTS));
        HttpHeaders staffHeaders = loginHeaders("recepcao@staffrestrito.com", "SenhaForte123");

        ResponseEntity<List<Object>> appointments = restTemplate.exchange(
                url("/api/appointments"), HttpMethod.GET, new HttpEntity<>(staffHeaders),
                new ParameterizedTypeReference<List<Object>>() { });
        assertThat(appointments.getStatusCode()).isEqualTo(HttpStatus.OK);

        assertThat(restTemplate.exchange(
                url("/api/patients"), HttpMethod.GET, new HttpEntity<>(staffHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(
                url("/api/finance/receivables"), HttpMethod.GET, new HttpEntity<>(staffHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(
                url("/api/services"), HttpMethod.GET, new HttpEntity<>(staffHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(restTemplate.exchange(
                url("/api/users"), HttpMethod.GET, new HttpEntity<>(staffHeaders), ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);

        assertThat(staff.permissions()).containsExactly("MANAGE_APPOINTMENTS");
    }

    @Test
    void getDoctors_succeedsForStaffWithZeroPermissions() {
        HttpHeaders adminHeaders = headersFrom(registerClinic("Clinica Staff Sem Permissao", "admin@staffsempermissao.com"));

        createStaff(adminHeaders, "Sem Permissao", "sempermissao@staffsempermissao.com", Set.of());
        HttpHeaders staffHeaders = loginHeaders("sempermissao@staffsempermissao.com", "SenhaForte123");

        ResponseEntity<String> response = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.GET, new HttpEntity<>(staffHeaders), String.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void updateAndDeleteUsers_rejectAdminTarget_returns400() {
        AuthResponse auth = registerClinic("Clinica Protecao Admin", "admin@protecaoadmin.com");
        HttpHeaders adminHeaders = headersFrom(auth);
        Long adminUserId = auth.user().id();

        ResponseEntity<ApiError> updateResponse = restTemplate.exchange(
                url("/api/users/" + adminUserId), HttpMethod.PUT,
                new HttpEntity<>(new UserUpdateRequest("Hacked Name", "admin@protecaoadmin.com", Set.of()), adminHeaders),
                ApiError.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/users/" + adminUserId), HttpMethod.DELETE, new HttpEntity<>(adminHeaders), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listUsers_onlyReturnsUsersFromCallersOwnClinic() {
        HttpHeaders clinicAHeaders = headersFrom(registerClinic("Clinica Usuarios A", "admin@usuariosa.com"));
        createStaff(clinicAHeaders, "Staff A", "staffa@usuariosa.com", Set.of());

        HttpHeaders clinicBHeaders = headersFrom(registerClinic("Clinica Usuarios B", "admin@usuariosb.com"));
        createStaff(clinicBHeaders, "Staff B", "staffb@usuariosb.com", Set.of());

        ResponseEntity<List<UserManagementResponse>> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.GET, new HttpEntity<>(clinicAHeaders),
                new ParameterizedTypeReference<List<UserManagementResponse>>() { });

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).extracting(UserManagementResponse::email)
                .containsExactlyInAnyOrder("admin@usuariosa.com", "staffa@usuariosa.com");
    }

    private UserManagementResponse createStaff(
            HttpHeaders adminHeaders, String name, String email, Set<Permission> permissions) {
        ResponseEntity<UserManagementResponse> response = restTemplate.exchange(
                url("/api/users"), HttpMethod.POST,
                new HttpEntity<>(new UserCreateRequest(name, email, "SenhaForte123", permissions), adminHeaders),
                UserManagementResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private HttpHeaders loginHeaders(String email, String password) {
        ResponseEntity<AuthResponse> response = restTemplate.postForEntity(
                url("/api/auth/login"), new LoginRequest(email, password), AuthResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return headersFrom(response.getBody());
    }

    private AuthResponse registerClinic(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return registerResponse.getBody();
    }

    private HttpHeaders headersFrom(AuthResponse auth) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(auth.token());
        return headers;
    }
}
