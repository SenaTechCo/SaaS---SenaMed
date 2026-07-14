package com.senamed.backend.billing;

import com.senamed.backend.clinic.Clinic;
import com.senamed.backend.clinic.ClinicRepository;
import com.senamed.backend.clinic.ClinicStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Automatic clinic blocking (RF-022/RN-007, RF-026). Three independently-callable transitions, run
 * in sequence every tick - no per-row retry/external-I/O concern here (unlike Fase 4's mail
 * scheduler), just bulk timestamp comparisons, so one class is enough:
 *
 * <ol>
 *   <li>{@code TRIAL -> BLOCKED} directly at {@code trialEndsAt} - a trial has no paid period to
 *   be "past due" on.</li>
 *   <li>{@code ACTIVE -> PAST_DUE} the instant the current approved subscription's
 *   {@code current_period_end} passes.</li>
 *   <li>{@code PAST_DUE -> BLOCKED} after a further configurable grace window
 *   ({@code senamed.subscription.past-due-grace-days}).</li>
 * </ol>
 */
@Component
public class ClinicSubscriptionScheduler {

    private final ClinicRepository clinicRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final int pastDueGraceDays;

    public ClinicSubscriptionScheduler(
            ClinicRepository clinicRepository,
            SubscriptionRepository subscriptionRepository,
            @Value("${senamed.subscription.past-due-grace-days}") int pastDueGraceDays) {
        this.clinicRepository = clinicRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.pastDueGraceDays = pastDueGraceDays;
    }

    @Scheduled(fixedRateString = "${senamed.subscription.scheduler.fixed-rate-ms}")
    public void processClinicStatusTransitions() {
        blockExpiredTrials();
        markLapsedSubscriptionsPastDue();
        blockOverdueClinics();
    }

    @Transactional
    public void blockExpiredTrials() {
        List<Clinic> expiredTrials = clinicRepository.findByStatusAndTrialEndsAtBefore(ClinicStatus.TRIAL, Instant.now());
        expiredTrials.forEach(clinic -> clinic.setStatus(ClinicStatus.BLOCKED));
    }

    @Transactional
    public void markLapsedSubscriptionsPastDue() {
        List<Long> clinicIds = subscriptionRepository.findActiveClinicIdsWithLapsedApprovedSubscription(Instant.now());
        if (clinicIds.isEmpty()) {
            return;
        }
        clinicRepository.findAllById(clinicIds).forEach(clinic -> clinic.setStatus(ClinicStatus.PAST_DUE));
    }

    @Transactional
    public void blockOverdueClinics() {
        Instant cutoff = Instant.now().minus(pastDueGraceDays, ChronoUnit.DAYS);
        List<Long> clinicIds = subscriptionRepository.findPastDueClinicIdsBeyondGrace(cutoff);
        if (clinicIds.isEmpty()) {
            return;
        }
        clinicRepository.findAllById(clinicIds).forEach(clinic -> clinic.setStatus(ClinicStatus.BLOCKED));
    }
}
