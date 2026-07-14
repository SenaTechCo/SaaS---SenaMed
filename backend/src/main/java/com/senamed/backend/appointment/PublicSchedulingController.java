package com.senamed.backend.appointment;

import com.senamed.backend.appointment.dto.AppointmentCreateRequest;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.dto.AvailableSlotsResponse;
import com.senamed.backend.appointment.dto.PublicClinicResponse;
import com.senamed.backend.notification.AppointmentMessageService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public (unauthenticated) scheduling endpoints for patients - see {@link Appointment}'s javadoc
 * for the tenant-isolation caveat that applies to everything behind {@code /api/public/**}.
 */
@RestController
@RequestMapping("/api/public")
public class PublicSchedulingController {

    private final PublicSchedulingService schedulingService;
    private final AppointmentMessageService appointmentMessageService;

    public PublicSchedulingController(
            PublicSchedulingService schedulingService, AppointmentMessageService appointmentMessageService) {
        this.schedulingService = schedulingService;
        this.appointmentMessageService = appointmentMessageService;
    }

    @GetMapping("/clinics/{slug}")
    public PublicClinicResponse getClinic(@PathVariable String slug) {
        return schedulingService.getClinicBySlug(slug);
    }

    @GetMapping("/doctors/{doctorId}/available-slots")
    public AvailableSlotsResponse getAvailableSlots(
            @PathVariable Long doctorId, @RequestParam("date") String date) {
        return schedulingService.getAvailableSlots(doctorId, date);
    }

    @PostMapping("/appointments")
    public ResponseEntity<AppointmentResponse> create(@Valid @RequestBody AppointmentCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(schedulingService.create(request));
    }

    /** {@code token} is taken as a raw string (not {@code UUID}) so a malformed value is treated
     *  the same way as a well-formed but unknown one - a plain 404 - by {@link PublicSchedulingService#cancel}. */
    @PostMapping("/appointments/cancel/{token}")
    public AppointmentResponse cancel(@PathVariable("token") String token) {
        return schedulingService.cancel(token);
    }

    /** Same raw-string-token / malformed==unknown==404 pattern as {@link #cancel}. */
    @PostMapping("/appointments/confirm/{token}")
    public AppointmentResponse confirm(@PathVariable("token") String token) {
        return appointmentMessageService.confirmAttendance(token);
    }
}
