package com.senamed.backend.user;

import com.senamed.backend.common.EmailAlreadyExistsException;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.tenant.TenantContext;
import com.senamed.backend.user.dto.ChangePasswordRequest;
import com.senamed.backend.user.dto.UpdateProfileRequest;
import com.senamed.backend.user.dto.UserProfileResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads/updates the profile of the currently authenticated user (self-service "Configurações").
 * The current user is resolved from the {@code userId} claim carried in the JWT via
 * {@link TenantContext}, mirroring {@code ClinicService}'s pattern for the current clinic.
 */
@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public UserProfileResponse getCurrentProfile() {
        return UserProfileResponse.from(loadCurrentUser());
    }

    @Transactional
    public UserProfileResponse updateProfile(UpdateProfileRequest request) {
        User user = loadCurrentUser();
        if (!request.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }
        user.setName(request.name());
        user.setEmail(request.email());
        return UserProfileResponse.from(user);
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request) {
        User user = loadCurrentUser();
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new InvalidRequestException("Senha atual incorreta.");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
    }

    private User loadCurrentUser() {
        Long userId = TenantContext.currentUser().userId();
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found for the current session"));
    }
}
