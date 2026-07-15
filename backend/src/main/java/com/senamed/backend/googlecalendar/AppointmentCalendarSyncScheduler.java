package com.senamed.backend.googlecalendar;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Polls {@code appointment_calendar_sync_jobs} for {@code PENDING} rows every tick and delegates
 * each to {@link AppointmentCalendarSyncProcessor} - mirrors {@code AppointmentMessageScheduler}.
 * A job that fails simply stays {@code PENDING} (with an incremented attempt count) and gets
 * retried on the next tick.
 */
@Component
public class AppointmentCalendarSyncScheduler {

    private final AppointmentCalendarSyncJobRepository syncJobRepository;
    private final AppointmentCalendarSyncProcessor processor;

    public AppointmentCalendarSyncScheduler(
            AppointmentCalendarSyncJobRepository syncJobRepository, AppointmentCalendarSyncProcessor processor) {
        this.syncJobRepository = syncJobRepository;
        this.processor = processor;
    }

    @Scheduled(fixedRateString = "${senamed.google.calendar-sync-scheduler.fixed-rate-ms}")
    public void processPendingJobs() {
        List<Long> pendingIds = syncJobRepository.findByStatusOrderByCreatedAtAsc(SyncJobStatus.PENDING).stream()
                .map(AppointmentCalendarSyncJob::getId)
                .toList();

        pendingIds.forEach(processor::processOne);
    }
}
