package com.senamed.backend.notification;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.appointment.AppointmentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * Sends (or retries) a single {@link AppointmentMessage}. Kept as a separate bean from
 * {@link AppointmentMessageScheduler} so {@link #processOne}'s {@code @Transactional} goes through
 * Spring's proxy - calling it via {@code this::processOne} from within the same class would bypass
 * the proxy (self-invocation) and silently run without a transaction.
 */
@Component
public class AppointmentMessageProcessor {

    private static final Logger log = LoggerFactory.getLogger(AppointmentMessageProcessor.class);

    private final AppointmentMessageRepository messageRepository;
    private final AppointmentMailSender mailSender;

    public AppointmentMessageProcessor(AppointmentMessageRepository messageRepository, AppointmentMailSender mailSender) {
        this.messageRepository = messageRepository;
        this.mailSender = mailSender;
    }

    @Transactional
    public void processOne(Long messageId) {
        AppointmentMessage message = messageRepository.findById(messageId).orElse(null);
        if (message == null || message.getStatus() != MessageStatus.PENDING) {
            return; // race guard: already handled by a concurrent tick
        }

        Appointment appointment = message.getAppointment();
        if (appointment.getStatus() == AppointmentStatus.CANCELLED) {
            return; // defensive; onAppointmentCancelled should already have flipped this row
        }

        try {
            if (message.getType() == MessageType.CREATED_CONFIRMATION) {
                mailSender.sendCreatedConfirmation(appointment);
            } else {
                mailSender.sendReminder(appointment, message);
            }
            message.markSent(LocalDateTime.now());
        } catch (Exception ex) {
            log.warn("Failed to send appointment message {} (attempt {}): {}",
                    messageId, message.getAttempts() + 1, ex.getMessage());
            message.recordFailedAttempt();
        }
    }
}
