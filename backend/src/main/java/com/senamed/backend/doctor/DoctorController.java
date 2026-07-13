package com.senamed.backend.doctor;

import com.senamed.backend.doctor.dto.AvailabilityRequest;
import com.senamed.backend.doctor.dto.AvailabilityResponse;
import com.senamed.backend.doctor.dto.DoctorCreateRequest;
import com.senamed.backend.doctor.dto.DoctorResponse;
import com.senamed.backend.doctor.dto.DoctorUpdateRequest;
import com.senamed.backend.doctor.dto.TimeOffRequest;
import com.senamed.backend.doctor.dto.TimeOffResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    private final DoctorService doctorService;

    public DoctorController(DoctorService doctorService) {
        this.doctorService = doctorService;
    }

    @PostMapping
    public ResponseEntity<DoctorResponse> create(@Valid @RequestBody DoctorCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.create(request));
    }

    @GetMapping
    public List<DoctorResponse> listAll() {
        return doctorService.listAll();
    }

    @GetMapping("/{id}")
    public DoctorResponse getOne(@PathVariable Long id) {
        return doctorService.getOne(id);
    }

    @PutMapping("/{id}")
    public DoctorResponse update(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
        return doctorService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        doctorService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    /** Replaces ALL of the doctor's weekly availability windows - see {@link DoctorService}. */
    @PostMapping("/{id}/availability")
    public List<AvailabilityResponse> replaceAvailability(
            @PathVariable("id") Long doctorId, @Valid @RequestBody List<AvailabilityRequest> windows) {
        return doctorService.replaceAvailability(doctorId, windows);
    }

    @GetMapping("/{id}/availability")
    public List<AvailabilityResponse> listAvailability(@PathVariable("id") Long doctorId) {
        return doctorService.listAvailability(doctorId);
    }

    /** Time-off periods accumulate - this always creates a new record, never replaces existing ones. */
    @PostMapping("/{id}/time-off")
    public ResponseEntity<TimeOffResponse> createTimeOff(
            @PathVariable("id") Long doctorId, @Valid @RequestBody TimeOffRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(doctorService.createTimeOff(doctorId, request));
    }

    @GetMapping("/{id}/time-off")
    public List<TimeOffResponse> listTimeOff(@PathVariable("id") Long doctorId) {
        return doctorService.listTimeOff(doctorId);
    }
}
