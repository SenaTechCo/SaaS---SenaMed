package com.senamed.backend.doctor;

import com.senamed.backend.common.DoctorAccessAlreadyExistsException;
import com.senamed.backend.common.EmailAlreadyExistsException;
import com.senamed.backend.common.ResourceNotFoundException;
import com.senamed.backend.doctor.dto.DoctorAccessResponse;
import com.senamed.backend.doctor.dto.GrantDoctorAccessRequest;
import com.senamed.backend.tenant.TenantContext;
import com.senamed.backend.user.User;
import com.senamed.backend.user.UserRepository;
import com.senamed.backend.user.UserRole;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Grants/revokes a doctor's own login (KAN-77) - kept separate from {@link DoctorService} since it
 * needs {@link UserRepository}/{@link PasswordEncoder}, which that service has no other reason to
 * depend on.
 */
@Service
public class DoctorAccessService {

    private final DoctorRepository doctorRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DoctorAccessService(DoctorRepository doctorRepository, UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.doctorRepository = doctorRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public DoctorAccessResponse grantAccess(Long doctorId, GrantDoctorAccessRequest request) {
        Doctor doctor = loadOwnedDoctor(doctorId);

        if (userRepository.existsByDoctorId(doctor.getId())) {
            throw new DoctorAccessAlreadyExistsException(doctor.getId());
        }
        if (userRepository.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyExistsException(request.email());
        }

        User user = new User(
                doctor.getClinic(), doctor.getName(), request.email(),
                passwordEncoder.encode(request.password()), UserRole.DOCTOR, doctor);
        user = userRepository.save(user);
        return new DoctorAccessResponse(user.getId(), doctor.getId(), user.getEmail());
    }

    @Transactional
    public void revokeAccess(Long doctorId) {
        Doctor doctor = loadOwnedDoctor(doctorId);
        User user = userRepository.findByDoctorId(doctor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("No login exists for doctor: " + doctorId));
        userRepository.delete(user);
    }

    /** Same tenant-scoped-lookup pattern as {@code DoctorService.loadOwnedDoctor}. */
    private Doctor loadOwnedDoctor(Long id) {
        return doctorRepository.findByIdAndClinicId(id, TenantContext.currentClinicId())
                .orElseThrow(() -> new ResourceNotFoundException("Doctor not found: " + id));
    }
}
