package com.senamed.backend.notification;

import com.senamed.backend.appointment.Appointment;
import com.senamed.backend.notification.whatsapp.WhatsAppClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * WhatsApp counterpart to {@link AppointmentMailSender} (KAN-79) - same two message types, same
 * link-building logic, delivered via {@link WhatsAppClient} instead of {@code JavaMailSender}.
 * Reuses each {@link AppointmentMessage} row's own {@code confirmationToken}, so the existing
 * public confirm/cancel flows work unchanged regardless of which channel a link arrived through.
 *
 * <p>The parameter lists below are a best-effort default - their exact count/order must match
 * whatever templates are actually approved in Meta Business Manager for
 * {@code senamed.whatsapp.template.*}; verify and adjust once real templates exist.</p>
 */
@Component
public class AppointmentWhatsAppSender {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final WhatsAppClient whatsAppClient;
    private final String frontendBaseUrl;
    private final String createdConfirmationTemplate;
    private final String reminderTemplate;

    public AppointmentWhatsAppSender(
            WhatsAppClient whatsAppClient,
            @Value("${senamed.frontend.base-url}") String frontendBaseUrl,
            @Value("${senamed.whatsapp.template.created-confirmation}") String createdConfirmationTemplate,
            @Value("${senamed.whatsapp.template.reminder}") String reminderTemplate) {
        this.whatsAppClient = whatsAppClient;
        this.frontendBaseUrl = frontendBaseUrl;
        this.createdConfirmationTemplate = createdConfirmationTemplate;
        this.reminderTemplate = reminderTemplate;
    }

    public void sendCreatedConfirmation(Appointment appointment) {
        String cancelLink = frontendBaseUrl + "/cancelar/" + appointment.getCancelToken();

        whatsAppClient.sendTemplateMessage(appointment.getPatientPhone(), createdConfirmationTemplate, List.of(
                appointment.getPatientName(),
                appointment.getDoctor().getName(),
                appointment.getClinic().getName(),
                appointment.getStartsAt().format(DATE_TIME_FORMAT),
                cancelLink));
    }

    public void sendReminder(Appointment appointment, AppointmentMessage message) {
        String confirmLink = frontendBaseUrl + "/confirmar/" + message.getConfirmationToken();
        String cancelLink = frontendBaseUrl + "/cancelar/" + appointment.getCancelToken();

        whatsAppClient.sendTemplateMessage(appointment.getPatientPhone(), reminderTemplate, List.of(
                appointment.getPatientName(),
                appointment.getDoctor().getName(),
                appointment.getClinic().getName(),
                appointment.getStartsAt().format(DATE_TIME_FORMAT),
                confirmLink,
                cancelLink));
    }
}
