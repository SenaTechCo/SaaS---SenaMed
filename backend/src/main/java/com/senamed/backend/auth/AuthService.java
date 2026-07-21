package com.senamed.backend.auth;

import com.senamed.backend.auth.dto.AuthClinicDto;
import com.senamed.backend.auth.dto.AuthResponse;
import com.senamed.backend.auth.dto.AuthUserDto;
import com.senamed.backend.auth.dto.LoginRequest;
import com.senamed.backend.auth.dto.RegisterClinicRequest;
import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.EmailAlreadyExistsException;
import com.senamed.backend.common.InvalidCredentialsException;
import com.senamed.backend.security.JwtService;
import com.senamed.backend.user.User;
import com.senamed.backend.user.UserRepository;
import com.senamed.backend.user.UserRole;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Handles clinic self-registration (RF-001) and login (RF-003).
 */
@Service
public class AuthService {

    private final ClinicRepository clinicRepository;
    private final UserRepository userRepository;
    private final SlugService slugService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final int trialDays;

    public AuthService(
            ClinicRepository clinicRepository,
            UserRepository userRepository,
            SlugService slugService,
            PasswordEncoder passwordEncoder,
            JwtService jwtService,
            @Value("${senamed.trial.days}") int trialDays) {
        this.clinicRepository = clinicRepository;
        this.userRepository = userRepository;
        this.slugService = slugService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.trialDays = trialDays;
    }

    @Transactional
    public AuthResponse registerClinic(RegisterClinicRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        String slug = slugService.generateUniqueSlug(request.clinicName());
        Instant trialEndsAt = Instant.now().plus(trialDays, ChronoUnit.DAYS);

        Clinic clinic = new Clinic(request.clinicName(), slug, trialEndsAt);
        clinic = clinicRepository.save(clinic);

        User user = new User(
                clinic,
                request.adminName(),
                request.email(),
                passwordEncoder.encode(request.password()),
                UserRole.ADMIN);
        user = userRepository.save(user);

        return buildAuthResponse(clinic, user);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmailIgnoreCase(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new InvalidCredentialsException();
        }

        return buildAuthResponse(user.getClinic(), user);
    }

    private AuthResponse buildAuthResponse(Clinic clinic, User user) {
        Long doctorId = user.getDoctor() != null ? user.getDoctor().getId() : null;
        String token = jwtService.generateToken(
                user.getId(), clinic.getId(), user.getEmail(), user.getRole().name(), doctorId,
                user.effectivePermissions());
        return new AuthResponse(token, AuthClinicDto.from(clinic), AuthUserDto.from(user));
    }
}
