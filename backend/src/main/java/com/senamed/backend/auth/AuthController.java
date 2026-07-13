package com.senamed.backend.auth;

import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register-clinic")
    public ResponseEntity<AuthResponse> registerClinic(@Valid @RequestBody RegisterClinicRequest request) {
        AuthResponse response = authService.registerClinic(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
