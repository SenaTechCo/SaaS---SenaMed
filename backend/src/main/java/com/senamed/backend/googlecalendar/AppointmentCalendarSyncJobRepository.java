package com.senamed.backend.googlecalendar;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface AppointmentCalendarSyncJobRepository extends JpaRepository<AppointmentCalendarSyncJob, Long> {

    /** Backs the scheduler's poll for due jobs. */
    List<AppointmentCalendarSyncJob> findByStatusOrderByCreatedAtAsc(SyncJobStatus status);

    boolean existsByAppointmentIdAndTypeAndStatus(Long appointmentId, SyncJobType type, SyncJobStatus status);

    Optional<AppointmentCalendarSyncJob> findFirstByAppointmentIdAndTypeAndStatusOrderByCreatedAtDesc(
            Long appointmentId, SyncJobType type, SyncJobStatus status);

    /** Skips any not-yet-sent CREATE_EVENT jobs for an appointment that just got cancelled. */
    @Modifying
    @Query("UPDATE AppointmentCalendarSyncJob j SET j.status = com.senamed.backend.googlecalendar.SyncJobStatus.SKIPPED "
            + "WHERE j.appointment.id = :appointmentId AND j.type = com.senamed.backend.googlecalendar.SyncJobType.CREATE_EVENT "
            + "AND j.status = com.senamed.backend.googlecalendar.SyncJobStatus.PENDING")
    int skipPendingCreateJobsByAppointmentId(@Param("appointmentId") Long appointmentId);
}
