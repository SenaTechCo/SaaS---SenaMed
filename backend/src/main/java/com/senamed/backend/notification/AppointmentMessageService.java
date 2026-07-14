package com.senamed.backend.notification;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentRepository;
import com.senamed.backend.appointment.AppointmentStatus;
import com.senamed.backend.appointment.dto.AppointmentResponse;
import com.senamed.backend.appointment.event.AppointmentCancelledEvent;
import com.senamed.backend.appointment.event.AppointmentCreatedEvent;
import com.senamed.backend.common.AppointmentConflictException;
import com.senamed.backend.common.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Owns the {@code appointment_messages} outbox: creates rows in response to booking/cancellation
 * events (published by {@code PublicSchedulingService} after its transaction commits - see
 * {@link AppointmentCreatedEvent}'s javadoc for why this re-fetches the entity rather than trusting
 * the event payload) and resolves the public "confirm attendance" flow (RF-016).
 *
 * <p>Depends on the {@code appointment} package, never the reverse - keeps
 * {@code PublicSchedulingService} free of any notification-specific concerns.</p>
 */
@Service
public class AppointmentMessageService {

    private final AppointmentRepository appointmentRepository;
    private final AppointmentMessageRepository messageRepository;

    public AppointmentMessageService(
            AppointmentRepository appointmentRepository, AppointmentMessageRepository messageRepository) {
        this.appointmentRepository = appointmentRepository;
        this.messageRepository = messageRepository;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCreated(AppointmentCreatedEvent event) {
        Appointment appointment = appointmentRepository.findById(event.appointmentId())
                .orElseThrow(() -> new IllegalStateException("Appointment not found: " + event.appointmentId()));

        messageRepository.save(new AppointmentMessage(
                appointment, MessageType.CREATED_CONFIRMATION, LocalDateTime.now(), null));

        // RN-018: the confirm link stops making sense once the appointment time has passed.
        messageRepository.save(new AppointmentMessage(
                appointment, MessageType.REMINDER_24H,
                appointment.getStartsAt().minusHours(24), appointment.getStartsAt()));
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAppointmentCancelled(AppointmentCancelledEvent event) {
        messageRepository.cancelPendingByAppointmentId(event.appointmentId());
    }

    /** Mirrors {@code PublicSchedulingService.cancel}'s raw-string/malformed==unknown==404 pattern. */
    @Transactional
    public AppointmentResponse confirmAttendance(String rawToken) {
        UUID token;
        try {
            token = UUID.fromString(rawToken);
        } catch (IllegalArgumentException ex) {
            throw new ResourceNotFoundException("Agendamento não encontrado");
        }

        AppointmentMessage message = messageRepository.findByConfirmationToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Agendamento não encontrado"));
        Appointment appointment = message.getAppointment();

        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            throw new AppointmentConflictException("Este agendamento foi cancelado.");
        }
        if (appointment.getConfirmedAt() != null) {
            throw new AppointmentConflictException("Presença já confirmada.");
        }
        if (message.getTokenExpiresAt() == null || LocalDateTime.now().isAfter(message.getTokenExpiresAt())) {
            throw new AppointmentConflictException("Link de confirmação expirado.");
        }

        appointment.confirm();
        return AppointmentResponse.from(appointment);
    }
}
