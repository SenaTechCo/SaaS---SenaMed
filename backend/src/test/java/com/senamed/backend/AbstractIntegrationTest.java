package com.senamed.backend;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Base class for full-stack integration tests: boots the real Spring context on a random port.
 *
 * <p>Note on infrastructure: this project prefers Testcontainers for integration tests, but in
 * this development environment Docker Desktop's engine API response was not compatible with the
 * docker-java client bundled with Testcontainers 1.20.4 (a known friction point on some Windows
 * Docker Desktop versions), so tests fall back to the Postgres instance already provided by the
 * root {@code docker-compose.yml} (service {@code postgres}, container {@code senamed-postgres},
 * reachable at {@code localhost:5432}), as allowed by the project brief. Each test truncates the
 * tables it touches before running, so tests remain repeatable and independent of leftover data
 * from previous runs.</p>
 */
@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {

    @LocalServerPort
    protected int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    protected TestRestTemplate restTemplate = new TestRestTemplate();

    @BeforeEach
    void cleanDatabase() {
        // "plans" is deliberately NOT truncated - it's migration-seeded reference data (Fase 5),
        // not per-test data; Flyway only runs once against the test DB, so wiping it here would
        // permanently remove the 3 seed rows before the very first test.
        jdbcTemplate.execute(
                "TRUNCATE TABLE appointment_calendar_sync_jobs, doctor_google_calendar_credentials, "
                        + "appointments, doctor_time_off, doctor_availability, doctors, users, "
                        + "clinics, subscriptions, preapproval_charges, preapprovals RESTART IDENTITY CASCADE");
    }

    protected String url(String path) {
        return "http://localhost:" + port + path;
    }
}
