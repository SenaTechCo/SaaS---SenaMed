package com.senamed.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface PreapprovalRepository extends JpaRepository<Preapproval, Long> {

    Optional<Preapproval> findFirstByClinicIdOrderByCreatedAtDesc(Long clinicId);

    /**
     * Clinics currently {@code ACTIVE} whose most recent {@code AUTHORIZED} preapproval's paid
     * period has already ended - candidates for the {@code ACTIVE -> PAST_DUE} transition. Mirrors
     * {@code SubscriptionRepository.findActiveClinicIdsWithLapsedApprovedSubscription}.
     */
    @Query(value = """
            SELECT c.id
            FROM clinics c
            JOIN LATERAL (
                SELECT p.current_period_end AS current_period_end
                FROM preapprovals p
                WHERE p.clinic_id = c.id AND p.status = 'AUTHORIZED'
                ORDER BY p.created_at DESC
                LIMIT 1
            ) latest ON true
            WHERE c.status = 'ACTIVE' AND latest.current_period_end < :now
            """, nativeQuery = true)
    List<Long> findActiveClinicIdsWithLapsedAuthorizedPreapproval(@Param("now") Instant now);

    /**
     * Clinics currently {@code PAST_DUE} whose most recent {@code AUTHORIZED} preapproval's paid
     * period ended before the grace cutoff - candidates for the {@code PAST_DUE -> BLOCKED}
     * transition. Mirrors {@code SubscriptionRepository.findPastDueClinicIdsBeyondGrace}.
     */
    @Query(value = """
            SELECT c.id
            FROM clinics c
            JOIN LATERAL (
                SELECT p.current_period_end AS current_period_end
                FROM preapprovals p
                WHERE p.clinic_id = c.id AND p.status = 'AUTHORIZED'
                ORDER BY p.created_at DESC
                LIMIT 1
            ) latest ON true
            WHERE c.status = 'PAST_DUE' AND latest.current_period_end < :cutoff
            """, nativeQuery = true)
    List<Long> findPastDueClinicIdsBeyondGraceViaPreapproval(@Param("cutoff") Instant cutoff);
}
