package com.senamed.backend.user;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    boolean existsByEmailIgnoreCase(String email);

    Optional<User> findByEmailIgnoreCase(String email);

    boolean existsByDoctorId(Long doctorId);

    Optional<User> findByDoctorId(Long doctorId);

    /**
     * Explicit tenant-scoped lookup (defense in depth), mirroring
     * {@code DoctorRepository.findAllByClinicIdOrderByNameAsc}. Used by the {@code /api/users}
     * admin management endpoints.
     */
    List<User> findAllByClinicId(Long clinicId);

    /**
     * Same tenant-scoped-lookup pattern as {@code DoctorRepository.findByIdAndClinicId} - used by
     * every user-management operation instead of the plain {@code findById}.
     */
    Optional<User> findByIdAndClinicId(Long id, Long clinicId);
}
