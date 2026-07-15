package com.senamed.backend.googlecalendar;

import com.senamed.backend.googlecalendar.dto.ConnectUrlResponse;
import com.senamed.backend.googlecalendar.dto.GoogleCalendarStatusResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Doctor-authenticated status/connect/disconnect endpoints. Nested under {@code
 * /api/doctors/me/**}, which {@code SecurityConfig} already leaves at plain {@code authenticated()}
 * - {@link DoctorGoogleCalendarService} is what actually restricts these to DOCTOR-role callers,
 * via {@code TenantContext.currentDoctorId()} (same convention as every other
 * {@code /api/doctors/me/**} endpoint).
 */
@RestController
@RequestMapping("/api/doctors/me/google-calendar")
public class GoogleCalendarConnectionController {

    private final DoctorGoogleCalendarService service;

    public GoogleCalendarConnectionController(DoctorGoogleCalendarService service) {
        this.service = service;
    }

    @GetMapping
    public GoogleCalendarStatusResponse getStatus() {
        return service.getStatus();
    }

    @GetMapping("/connect-url")
    public ConnectUrlResponse getConnectUrl() {
        return service.getConnectUrl();
    }

    @DeleteMapping
    public ResponseEntity<Void> disconnect() {
        service.disconnect();
        return ResponseEntity.noContent().build();
    }
}
