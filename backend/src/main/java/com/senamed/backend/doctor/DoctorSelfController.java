package com.senamed.backend.doctor;

import com.senamed.backend.appointment.AppointmentService;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.TimeOffResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Read-only self-service views for a DOCTOR-role caller (KAN-77) - profile, own agenda,
 * availability, and time-off. All ADMIN-managed mutation stays exclusively under
 * {@link DoctorController}.
 */
@RestController
@RequestMapping("/api/doctors/me")
public class DoctorSelfController {

    private final DoctorService doctorService;
    private final AppointmentService appointmentService;

    public DoctorSelfController(DoctorService doctorService, AppointmentService appointmentService) {
        this.doctorService = doctorService;
        this.appointmentService = appointmentService;
    }

    @GetMapping
    public DoctorResponse getMyProfile() {
        return doctorService.getMyProfile();
    }

    @GetMapping("/appointments")
    public List<AppointmentResponse> listMyAppointments() {
        return appointmentService.listMine();
    }

    @GetMapping("/availability")
    public List<AvailabilityResponse> listMyAvailability() {
        return doctorService.listMyAvailability();
    }

    @GetMapping("/time-off")
    public List<TimeOffResponse> listMyTimeOff() {
        return doctorService.listMyTimeOff();
    }
}
