package com.senamed.backend.catalog;

import com.senamed.backend.catalog.dto.ServiceOfferingCreateRequest;
import com.senamed.backend.catalog.dto.ServiceOfferingResponse;
import com.senamed.backend.catalog.dto.ServiceOfferingUpdateRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/services")
public class ServiceOfferingController {

    private final ServiceOfferingService serviceOfferingService;

    public ServiceOfferingController(ServiceOfferingService serviceOfferingService) {
        this.serviceOfferingService = serviceOfferingService;
    }

    @PostMapping
    public ResponseEntity<ServiceOfferingResponse> create(@Valid @RequestBody ServiceOfferingCreateRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(serviceOfferingService.create(request));
    }

    @GetMapping
    public List<ServiceOfferingResponse> listAll(@RequestParam(required = false) String search) {
        return serviceOfferingService.listAll(search);
    }

    @GetMapping("/{id}")
    public ServiceOfferingResponse getOne(@PathVariable Long id) {
        return serviceOfferingService.getOne(id);
    }

    @PutMapping("/{id}")
    public ServiceOfferingResponse update(@PathVariable Long id, @Valid @RequestBody ServiceOfferingUpdateRequest request) {
        return serviceOfferingService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deactivate(@PathVariable Long id) {
        serviceOfferingService.deactivate(id);
        return ResponseEntity.noContent().build();
    }

    @PatchMapping("/{id}/restaurar")
    public ServiceOfferingResponse restore(@PathVariable Long id) {
        return serviceOfferingService.restore(id);
    }
}
