package com.senamed.backend.googlecalendar;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;

/**
 * Public (unauthenticated) endpoint Google calls directly after the doctor consents/denies on its
 * own consent screen - see {@link com.senamed.backend.config.SecurityConfig}, which already
 * {@code permitAll()}s the whole {@code /api/public/**} prefix, so no matcher change was needed
 * for this. Never returns a JSON error to the browser - always redirects back to the frontend with
 * a {@code ?status=} query param, mirroring how Mercado Pago Checkout Pro's back_urls work.
 */
@RestController
@RequestMapping("/api/public/google-calendar")
public class GoogleCalendarCallbackController {

    private static final Logger log = LoggerFactory.getLogger(GoogleCalendarCallbackController.class);

    private final DoctorGoogleCalendarService service;
    private final String frontendBaseUrl;

    public GoogleCalendarCallbackController(
            DoctorGoogleCalendarService service, @Value("${senamed.frontend.base-url}") String frontendBaseUrl) {
        this.service = service;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @GetMapping("/callback")
    public ResponseEntity<Void> callback(
            @RequestParam(required = false) String code,
            @RequestParam(required = false) String state,
            @RequestParam(required = false) String error) {
        boolean connected = false;
        if (error == null && code != null && state != null) {
            try {
                connected = service.handleCallback(code, state);
            } catch (RuntimeException ex) {
                log.warn("Failed to complete Google Calendar OAuth callback: {}", ex.getMessage());
            }
        }

        String status = connected ? "connected" : "error";
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(frontendBaseUrl + "/dashboard/google-calendar?status=" + status))
                .build();
    }
}
