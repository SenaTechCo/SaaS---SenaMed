package com.senamed.backend.billing;

import com.senamed.backend.AbstractIntegrationTest;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Covers Fase 5 (KAN-68): the three automatic clinic-blocking transitions. */
class ClinicSubscriptionSchedulerTest extends AbstractIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ClinicSubscriptionScheduler scheduler;

    @Test
    void blockExpiredTrials_blocksTrialClinicPastItsGracePeriod() {
        Long clinicId = registerClinicAndGetId("Clinica Trial Expirado", "admin@trialexpirado.com");
        jdbcTemplate.update(
                "UPDATE clinics SET trial_ends_at = ? WHERE id = ?",
                Timestamp.from(Instant.now().minus(1, ChronoUnit.DAYS)), clinicId);

        scheduler.blockExpiredTrials();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("BLOCKED");
    }

    @Test
    void markLapsedSubscriptionsPastDue_movesActiveClinicWithExpiredPeriodToPastDue() {
        Long clinicId = registerClinicAndGetId("Clinica Periodo Vencido", "admin@periodovencido.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        jdbcTemplate.update("UPDATE clinics SET status = 'ACTIVE' WHERE id = ?", clinicId);
        insertApprovedSubscription(clinicId, planId, Instant.now().minus(1, ChronoUnit.DAYS));

        scheduler.markLapsedSubscriptionsPastDue();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("PAST_DUE");
    }

    @Test
    void blockOverdueClinics_blocksPastDueClinicBeyondGraceWindow() {
        Long clinicId = registerClinicAndGetId("Clinica Atraso Longo", "admin@atrasolongo.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        jdbcTemplate.update("UPDATE clinics SET status = 'PAST_DUE' WHERE id = ?", clinicId);
        // Well beyond the default 3-day grace window.
        insertApprovedSubscription(clinicId, planId, Instant.now().minus(10, ChronoUnit.DAYS));

        scheduler.blockOverdueClinics();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("BLOCKED");
    }

    @Test
    void blockOverdueClinics_leavesPastDueClinicUntouchedWithinGraceWindow() {
        Long clinicId = registerClinicAndGetId("Clinica Atraso Recente", "admin@atrasorecente.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        jdbcTemplate.update("UPDATE clinics SET status = 'PAST_DUE' WHERE id = ?", clinicId);
        // Within the default 3-day grace window.
        insertApprovedSubscription(clinicId, planId, Instant.now().minus(1, ChronoUnit.DAYS));

        scheduler.blockOverdueClinics();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("PAST_DUE");
    }

    @Test
    void markLapsedSubscriptionsPastDue_movesActiveClinicWithExpiredPreapprovalToPastDue() {
        Long clinicId = registerClinicAndGetId("Clinica Preapproval Vencido", "admin@preapprovalvencido.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        jdbcTemplate.update("UPDATE clinics SET status = 'ACTIVE' WHERE id = ?", clinicId);
        insertAuthorizedPreapproval(clinicId, planId, Instant.now().minus(1, ChronoUnit.DAYS));

        scheduler.markLapsedSubscriptionsPastDue();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("PAST_DUE");
    }

    @Test
    void blockOverdueClinics_blocksPastDuePreapprovalClinicBeyondGraceWindow() {
        Long clinicId = registerClinicAndGetId("Clinica Preapproval Atraso Longo", "admin@preapprovalatrasolongo.com");
        Long planId = jdbcTemplate.queryForObject("SELECT id FROM plans WHERE name = 'Básico'", Long.class);
        jdbcTemplate.update("UPDATE clinics SET status = 'PAST_DUE' WHERE id = ?", clinicId);
        // Well beyond the default 3-day grace window.
        insertAuthorizedPreapproval(clinicId, planId, Instant.now().minus(10, ChronoUnit.DAYS));

        scheduler.blockOverdueClinics();

        assertThat(jdbcTemplate.queryForObject("SELECT status FROM clinics WHERE id = ?", String.class, clinicId))
                .isEqualTo("BLOCKED");
    }

    private void insertApprovedSubscription(Long clinicId, Long planId, Instant currentPeriodEnd) {
        jdbcTemplate.update(
                "INSERT INTO subscriptions (clinic_id, plan_id, status, period_months, current_period_start, current_period_end) "
                        + "VALUES (?, ?, 'APPROVED', 1, ?, ?)",
                clinicId, planId, Timestamp.from(currentPeriodEnd.minus(30, ChronoUnit.DAYS)), Timestamp.from(currentPeriodEnd));
    }

    private void insertAuthorizedPreapproval(Long clinicId, Long planId, Instant currentPeriodEnd) {
        jdbcTemplate.update(
                "INSERT INTO preapprovals (clinic_id, plan_id, status, period_months, current_period_start, current_period_end) "
                        + "VALUES (?, ?, 'AUTHORIZED', 1, ?, ?)",
                clinicId, planId, Timestamp.from(currentPeriodEnd.minus(30, ChronoUnit.DAYS)), Timestamp.from(currentPeriodEnd));
    }

    private Long registerClinicAndGetId(String clinicName, String adminEmail) {
        RegisterClinicRequest registerRequest = new RegisterClinicRequest(clinicName, "Admin", adminEmail, "SenhaForte123");
        ResponseEntity<AuthResponse> registerResponse = restTemplate.postForEntity(
                url("/api/auth/register-clinic"), registerRequest, AuthResponse.class);
        assertThat(registerResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return registerResponse.getBody().clinic().id();
    }
}
