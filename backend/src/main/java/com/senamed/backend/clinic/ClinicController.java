package com.senamed.backend.clinic;

import com.senamed.backend.clinic.dto.ClinicProfileResponse;
import com.senamed.backend.clinic.dto.ClinicUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/clinics")
public class ClinicController {

    private final ClinicService clinicService;

    public ClinicController(ClinicService clinicService) {
        this.clinicService = clinicService;
    }

    @GetMapping("/me")
    public ClinicProfileResponse getMyClinic() {
        return clinicService.getCurrentClinic();
    }

    @PutMapping("/me")
    public ClinicProfileResponse updateMyClinic(@Valid @RequestBody ClinicUpdateRequest request) {
        return clinicService.updateCurrentClinic(request);
    }
}
