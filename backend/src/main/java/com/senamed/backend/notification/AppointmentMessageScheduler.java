package com.senamed.backend.notification;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Single unified job for both "send" and "reenvio de mensagens pendentes" (RF-023): every tick,
 * finds {@link MessageStatus#PENDING} rows whose {@code scheduledFor} is due and delegates each to
 * {@link AppointmentMessageProcessor}. A message that fails simply stays {@code PENDING} (with an
 * incremented attempt count) and gets retried on the next tick - no separate retry mechanism needed.
 */
@Component
public class AppointmentMessageScheduler {

    private final AppointmentMessageRepository messageRepository;
    private final AppointmentMessageProcessor processor;

    public AppointmentMessageScheduler(AppointmentMessageRepository messageRepository, AppointmentMessageProcessor processor) {
        this.messageRepository = messageRepository;
        this.processor = processor;
    }

    @Scheduled(fixedRateString = "${senamed.reminder.scheduler.fixed-rate-ms}")
    public void processDueMessages() {
        List<Long> dueIds = messageRepository
                .findByStatusAndScheduledForLessThanEqualOrderByScheduledForAsc(MessageStatus.PENDING, LocalDateTime.now())
                .stream()
                .map(AppointmentMessage::getId)
                .toList();

        dueIds.forEach(processor::processOne);
    }
}
