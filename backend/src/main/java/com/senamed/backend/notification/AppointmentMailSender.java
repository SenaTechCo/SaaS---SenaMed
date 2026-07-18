package com.senamed.backend.notification;

import com.senamed.backend.appointment.Appointment;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.time.format.DateTimeFormatter;

/**
 * Plain-text email sender for the two {@link MessageType}s. No templating engine - bodies are short
 * enough to build directly. Links point at the frontend (served separately from this API), so
 * {@code senamed.frontend.base-url} must be configured to match wherever the frontend actually runs.
 */
@Component
public class AppointmentMailSender {

    private static final DateTimeFormatter DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final JavaMailSender javaMailSender;

    @Value("${senamed.mail.from}")
    private String from;

    @Value("${senamed.frontend.base-url}")
    private String frontendBaseUrl;

    public AppointmentMailSender(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    public void sendCreatedConfirmation(Appointment appointment) {
        String clinicName = appointment.getClinic().getName();
        String cancelLink = frontendBaseUrl + "/cancelar/" + appointment.getCancelToken();

        String body = "Olá, " + appointment.getPatientName() + "!\n\n"
                + "Seu agendamento com " + appointment.getDoctor().getName() + " em " + clinicName
                + " foi confirmado para " + formatDateTime(appointment) + ".\n\n"
                + "Caso precise cancelar, acesse: " + cancelLink + "\n";

        send(appointment.getPatientEmail(), "Agendamento confirmado - " + clinicName, body);
    }

    public void sendReminder(Appointment appointment, AppointmentMessage message) {
        String clinicName = appointment.getClinic().getName();
        String confirmLink = frontendBaseUrl + "/confirmar/" + message.getConfirmationToken();
        String cancelLink = frontendBaseUrl + "/cancelar/" + appointment.getCancelToken();

        String body = "Olá, " + appointment.getPatientName() + "!\n\n"
                + "Este é um lembrete do seu agendamento com " + appointment.getDoctor().getName() + " em " + clinicName
                + ", amanhã, " + formatDateTime(appointment) + ".\n\n"
                + "Por favor, confirme sua presença: " + confirmLink + "\n\n"
                + "Caso não possa comparecer, cancele em: " + cancelLink + "\n";

        send(appointment.getPatientEmail(), "Lembrete: seu agendamento é amanhã - " + clinicName, body);
    }

    private void send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        javaMailSender.send(message);
    }

    private String formatDateTime(Appointment appointment) {
        return appointment.getStartsAt().format(DATE_TIME_FORMAT);
    }
}
