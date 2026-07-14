package com.senamed.backend.billing;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findFirstByClinicIdOrderByCreatedAtDesc(Long clinicId);

    /**
     * Clinics currently {@code ACTIVE} whose most recent {@code APPROVED} subscription's paid
     * period has already ended - candidates for the {@code ACTIVE -> PAST_DUE} transition.
     */
    @Query(value = """
            SELECT c.id
            FROM clinics c
            JOIN LATERAL (
                SELECT s.current_period_end AS current_period_end
                FROM subscriptions s
                WHERE s.clinic_id = c.id AND s.status = 'APPROVED'
                ORDER BY s.created_at DESC
                LIMIT 1
            ) latest ON true
            WHERE c.status = 'ACTIVE' AND latest.current_period_end < :now
            """, nativeQuery = true)
    List<Long> findActiveClinicIdsWithLapsedApprovedSubscription(@Param("now") Instant now);

    /**
     * Clinics currently {@code PAST_DUE} whose most recent {@code APPROVED} subscription's paid
     * period ended before the grace cutoff - candidates for the {@code PAST_DUE -> BLOCKED}
     * transition.
     */
    @Query(value = """
            SELECT c.id
            FROM clinics c
            JOIN LATERAL (
                SELECT s.current_period_end AS current_period_end
                FROM subscriptions s
                WHERE s.clinic_id = c.id AND s.status = 'APPROVED'
                ORDER BY s.created_at DESC
                LIMIT 1
            ) latest ON true
            WHERE c.status = 'PAST_DUE' AND latest.current_period_end < :cutoff
            """, nativeQuery = true)
    List<Long> findPastDueClinicIdsBeyondGrace(@Param("cutoff") Instant cutoff);
}
