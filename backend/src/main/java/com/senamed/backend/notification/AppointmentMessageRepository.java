package com.senamed.backend.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AppointmentMessageRepository extends JpaRepository<AppointmentMessage, Long> {

    Optional<AppointmentMessage> findByConfirmationToken(UUID confirmationToken);

    /** Backs the scheduler's poll for due messages (RF-023: envio e reenvio). */
    List<AppointmentMessage> findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(
            MessageStatus status, LocalDateTime now);

    /** Skips any not-yet-sent messages for an appointment that just got cancelled. */
    @Modifying
    @Query("UPDATE AppointmentMessage m SET m.status = com.senamed.backend.notification.MessageStatus.CANCELLED "
            + "WHERE m.appointment.id = :appointmentId AND m.status = com.senamed.backend.notification.MessageStatus.PENDING")
    int cancelPendingByAppointmentId(@Param("appointmentId") Long appointmentId);
}
