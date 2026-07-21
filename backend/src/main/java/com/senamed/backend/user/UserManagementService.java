package com.senamed.backend.user;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.common.EmailAlreadyExistsException;
import com.senamed.backend.common.InvalidRequestException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.tenant.TenantContext;
import com.senamed.backend.user.dto.UserCreateRequest;
import com.senamed.backend.user.dto.UserManagementResponse;
import com.senamed.backend.user.dto.UserUpdateRequest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

/**
 * Admin-only management of every {@link User} in the current clinic (list/create/edit/delete) -
 * kept separate from {@link UserService}, which is self-service-only ({@code /api/users/me}) and
 * has no reason to depend on {@link ClinicRepository} or cross-user tenant-scoped lookups.
 */
@Service
public class UserManagementService {

    private final UserRepository userRepository;
    private final ClinicRepository clinicRepository;
    private final PasswordEncoder passwordEncoder;

    public UserManagementService(
            UserRepository userRepository, ClinicRepository clinicRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.clinicRepository = clinicRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<UserManagementResponse> listAll() {
        return userRepository.findAllByClinicId(TenantContext.currentClinicId()).stream()
                .map(UserManagementResponse::from)
                .toList();
    }

    @Transactional
    public UserManagementResponse create(UserCreateRequest request) {
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        Long clinicId = TenantContext.currentClinicId();
        Clinic clinic = clinicRepository.findById(clinicId)
                .orElseThrow(() -> new ResourceNotFoundException("Clinic not found for the current session"));

        User user = new User(
                clinic, request.name(), request.email(), passwordEncoder.encode(request.password()), UserRole.STAFF);
        user.setPermissions(request.permissions() == null ? Set.of() : request.permissions());
        user = userRepository.save(user);
        return UserManagementResponse.from(user);
    }

    @Transactional
    public UserManagementResponse update(Long id, UserUpdateRequest request) {
        User user = loadOwnedUser(id);
        rejectIfAdmin(user);

        if (!request.email().equalsIgnoreCase(user.getEmail())
                && userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        user.setName(request.name());
        user.setEmail(request.email());
        user.setPermissions(request.permissions() == null ? Set.of() : request.permissions());
        return UserManagementResponse.from(user);
    }

    @Transactional
    public void delete(Long id) {
        User user = loadOwnedUser(id);
        rejectIfAdmin(user);
        userRepository.delete(user);
    }

    /**
     * Loads a user, explicitly scoped to the current clinic. Returns 404 (via
     * {@link ResourceNotFoundException}) both when the id does not exist and when it belongs to
     * another clinic, so callers cannot distinguish "not found" from "not yours" (same pattern as
     * {@code DoctorAccessService.loadOwnedDoctor}).
     */
    private User loadOwnedUser(Long id) {
        return userRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
    }

    /**
     * Hard safety rail: the clinic owner's ADMIN account can never be edited/deleted through this
     * generic endpoint, so nobody can accidentally (or maliciously) strip its access.
     */
    private void rejectIfAdmin(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            throw new InvalidRequestException("The clinic's ADMIN user cannot be edited or deleted here.");
        }
    }
}
