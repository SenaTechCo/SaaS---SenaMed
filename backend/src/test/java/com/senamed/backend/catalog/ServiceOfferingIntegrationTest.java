package com.senamed.backend.catalog;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.catalog.dto.ServiceOfferingUpdateRequest;
import com.senamed.backend.common.ApiError;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers the Catalogo de Servicos feature: CRUD, search, soft delete + restore, and tenant
 * isolation between two different clinics - mirrors {@code PatientFlowIntegrationTest}'s
 * conventions.
 */
class ServiceOfferingIntegrationTest extends AbstractIntegrationTest {

    @Test
    void createServiceOffering_happyPath_returns201AndListsIt() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Servicos Um", "admin1@servicos.com");

        ServiceOfferingResponse created = createServiceOffering(headers, "Consulta Inicial", 30, "150.00");
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Consulta Inicial");
        assertThat(created.durationMinutes()).isEqualTo(30);
        assertThat(created.price()).isEqualByComparingTo(new BigDecimal("150.00"));
        assertThat(created.active()).isTrue();
        assertThat(created.createdAt()).isNotNull();

        ResponseEntity<List<ServiceOfferingResponse>> listResponse = restTemplate.exchange(
                url("/api/services"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ServiceOfferingResponse>>() {
                });
        assertThat(listResponse.getBody()).extracting(ServiceOfferingResponse::name).containsExactly("Consulta Inicial");
    }

    @Test
    void createServiceOffering_blankName_returns400() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Servicos Validacao", "admin2@servicos.com");

        ServiceOfferingCreateRequest request = new ServiceOfferingCreateRequest("", null, 30, new BigDecimal("100.00"));
        ResponseEntity<ApiError> response = restTemplate.exchange(
                url("/api/services"), HttpMethod.POST, new HttpEntity<>(request, headers), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void listServiceOfferings_searchFiltersByName() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Servicos Busca", "admin3@servicos.com");
        createServiceOffering(headers, "Limpeza de Pele", 45, "120.00");
        createServiceOffering(headers, "Massagem Relaxante", 60, "180.00");

        ResponseEntity<List<ServiceOfferingResponse>> fullList = restTemplate.exchange(
                url("/api/services"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ServiceOfferingResponse>>() {
                });
        assertThat(fullList.getBody()).extracting(ServiceOfferingResponse::name)
                .containsExactlyInAnyOrder("Limpeza de Pele", "Massagem Relaxante");

        ResponseEntity<List<ServiceOfferingResponse>> searchResponse = restTemplate.exchange(
                url("/api/services?search=limpeza"), HttpMethod.GET, new HttpEntity<>(headers),
                new ParameterizedTypeReference<List<ServiceOfferingResponse>>() {
                });
        assertThat(searchResponse.getBody()).extracting(ServiceOfferingResponse::name).containsExactly("Limpeza de Pele");
    }

    @Test
    void updateServiceOffering_editsFields() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Servicos Edicao", "admin4@servicos.com");
        Long serviceId = createServiceOffering(headers, "Nome Antigo", 30, "100.00").id();

        ServiceOfferingUpdateRequest updateRequest = new ServiceOfferingUpdateRequest(
                "Nome Novo", "Descricao atualizada", 45, new BigDecimal("175.50"));
        ResponseEntity<ServiceOfferingResponse> updateResponse = restTemplate.exchange(
                url("/api/services/" + serviceId), HttpMethod.PUT, new HttpEntity<>(updateRequest, headers), ServiceOfferingResponse.class);

        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody().name()).isEqualTo("Nome Novo");
        assertThat(updateResponse.getBody().description()).isEqualTo("Descricao atualizada");
        assertThat(updateResponse.getBody().durationMinutes()).isEqualTo(45);
        assertThat(updateResponse.getBody().price()).isEqualByComparingTo(new BigDecimal("175.50"));
    }

    @Test
    void deactivateThenRestoreServiceOffering_softDeletesInsteadOfRemoving() {
        HttpHeaders headers = authHeadersForNewClinic("Clinica Servicos Inativacao", "admin5@servicos.com");
        Long serviceId = createServiceOffering(headers, "Servico Inativado", 30, "90.00").id();

        ResponseEntity<Void> deleteResponse = restTemplate.exchange(
                url("/api/services/" + serviceId), HttpMethod.DELETE, new HttpEntity<>(headers), Void.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<ServiceOfferingResponse> getResponse = restTemplate.exchange(
                url("/api/services/" + serviceId), HttpMethod.GET, new HttpEntity<>(headers), ServiceOfferingResponse.class);
        assertThat(getResponse.getBody().active()).isFalse();

        ResponseEntity<ServiceOfferingResponse> restoreResponse = restTemplate.exchange(
                url("/api/services/" + serviceId + "/restaurar"), HttpMethod.PATCH, new HttpEntity<>(headers), ServiceOfferingResponse.class);
        assertThat(restoreResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(restoreResponse.getBody().active()).isTrue();
    }

    @Test
    void serviceOfferings_areIsolatedBetweenClinics() {
        HttpHeaders clinicAHeaders = authHeadersForNewClinic("Clinica Servicos A", "adminA@servicos.com");
        HttpHeaders clinicBHeaders = authHeadersForNewClinic("Clinica Servicos B", "adminB@servicos.com");

        Long serviceOfClinicA = createServiceOffering(clinicAHeaders, "Servico Exclusivo A", 30, "50.00").id();

        ResponseEntity<ApiError> getResponse = restTemplate.exchange(
                url("/api/services/" + serviceOfClinicA), HttpMethod.GET, new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ServiceOfferingUpdateRequest updateRequest = new ServiceOfferingUpdateRequest(
                "Tentativa de Alteracao", null, 30, new BigDecimal("50.00"));
        ResponseEntity<ApiError> updateResponse = restTemplate.exchange(
                url("/api/services/" + serviceOfClinicA), HttpMethod.PUT,
                new HttpEntity<>(updateRequest, clinicBHeaders), ApiError.class);
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<ApiError> deleteResponse = restTemplate.exchange(
                url("/api/services/" + serviceOfClinicA), HttpMethod.DELETE, new HttpEntity<>(clinicBHeaders), ApiError.class);
        assertThat(deleteResponse.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<List<ServiceOfferingResponse>> clinicBList = restTemplate.exchange(
                url("/api/services"), HttpMethod.GET, new HttpEntity<>(clinicBHeaders),
                new ParameterizedTypeReference<List<ServiceOfferingResponse>>() {
                });
        assertThat(clinicBList.getBody()).isEmpty();
    }

    @Test
    void serviceOfferings_withoutToken_returns401() {
        ResponseEntity<ApiError> response = restTemplate.getForEntity(url("/api/services"), ApiError.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    private ServiceOfferingResponse createServiceOffering(HttpHeaders headers, String name, int durationMinutes, String price) {
        ServiceOfferingCreateRequest request = new ServiceOfferingCreateRequest(name, null, durationMinutes, new BigDecimal(price));
        ResponseEntity<ServiceOfferingResponse> response = restTemplate.exchange(
                url("/api/services"), HttpMethod.POST, new HttpEntity<>(request, headers), ServiceOfferingResponse.class);
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
