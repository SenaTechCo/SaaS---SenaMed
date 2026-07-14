package com.senamed.backend.appointment;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Covers Fase 6 / KAN-74 (secao 16 - concorrencia): fires many booking requests for the exact same
 * slot at once and asserts exactly one succeeds. This is what actually proves RN-005/RF-013's
 * anti-double-booking guarantee - {@link AppointmentFlowIntegrationTest}'s sequential
 * "createAppointment_slotAlreadyTaken_returns409" test only proves the optimistic Java-side check;
 * it cannot catch a race, since the two requests never overlap in time. Here they must.
 */
class AppointmentConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(7);
    private static final int FUTURE_DAY_OF_WEEK = FUTURE_DATE.getDayOfWeek().getValue();
    private static final int CONCURRENT_REQUESTS = 10;

    @Test
    void concurrentBookings_forTheSameSlot_onlyOneSucceeds() throws InterruptedException {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(
                "Clinica Concorrencia", "Admin", "admin@concorrencia.com", "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(registerResponse.getBody().token());

        ResponseEntity<DoctorResponse> doctorResponse = restTemplate.exchange(
                url("/api/doctors"), HttpMethod.POST,
                new HttpEntity<>(new DoctorCreateRequest("Dr. Concorrencia", "Clinico Geral", null, null), headers),
                DoctorResponse.class);
        Long doctorId = doctorResponse.getBody().id();

        List<AvailabilityRequest> windows = List.of(
                new AvailabilityRequest(FUTURE_DAY_OF_WEEK, LocalTime.of(9, 0), LocalTime.of(10, 0)));
        restTemplate.exchange(
                url("/api/doctors/" + doctorId + "/availability"), HttpMethod.POST,
                new HttpEntity<>(windows, headers),
                new ParameterizedTypeReference<List<AvailabilityResponse>>() {
                });

        ExecutorService executor = Executors.newFixedThreadPool(CONCURRENT_REQUESTS);
        CountDownLatch readyLatch = new CountDownLatch(CONCURRENT_REQUESTS);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger conflictCount = new AtomicInteger();

        for (int i = 0; i < CONCURRENT_REQUESTS; i++) {
            int index = i;
            executor.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }

                AppointmentCreateRequest request = new AppointmentCreateRequest(
                        doctorId, FUTURE_DATE, LocalTime.of(9, 0),
                        "Paciente " + index, "paciente" + index + "@concorrencia.com", null, true);
                ResponseEntity<String> response = restTemplate.postForEntity(
                        url("/api/public/appointments"), request, String.class);

                if (response.getStatusCode() == HttpStatus.CREATED) {
                    successCount.incrementAndGet();
                } else if (response.getStatusCode() == HttpStatus.CONFLICT) {
                    conflictCount.incrementAndGet();
                }
            });
        }

        readyLatch.await(5, TimeUnit.SECONDS); // all threads primed and waiting on startLatch
        startLatch.countDown(); // release them all at once
        executor.shutdown();
        assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(CONCURRENT_REQUESTS - 1);
    }
}
