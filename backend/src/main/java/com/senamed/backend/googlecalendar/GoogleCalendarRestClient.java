package com.senamed.backend.googlecalendar;

import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeRequestUrl;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.client.util.DateTime;
import com.google.api.services.calendar.Calendar;
import com.google.api.services.calendar.CalendarScopes;
import com.google.api.services.calendar.model.Event;
import com.google.api.services.calendar.model.EventDateTime;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

/**
 * Real implementation backed by Google's official OAuth2/Calendar client libraries (KAN-78).
 * Without a real Google Cloud OAuth client configured, every call fails - surfaced here as
 * {@link GoogleCalendarIntegrationException} - the same "fails gracefully until real credentials
 * exist" shape as {@code MercadoPagoRestClient}.
 */
@Component
public class GoogleCalendarRestClient implements GoogleCalendarClient {

    private static final List<String> SCOPES = List.of(CalendarScopes.CALENDAR_EVENTS, "email");
    private static final String CALENDAR_ID = "primary";

    private final HttpTransport httpTransport;
    private final GsonFactory jsonFactory = GsonFactory.getDefaultInstance();
    private final RestClient userInfoClient;
    private final String clientId;
    private final String clientSecret;
    private final String redirectUri;

    public GoogleCalendarRestClient(
            RestClient.Builder restClientBuilder,
            @Value("${senamed.google.client-id}") String clientId,
            @Value("${senamed.google.client-secret}") String clientSecret,
            @Value("${senamed.google.redirect-uri}") String redirectUri) {
        try {
            this.httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        } catch (GeneralSecurityException | IOException ex) {
            throw new IllegalStateException("Failed to initialize Google HTTP transport", ex);
        }
        this.userInfoClient = restClientBuilder.baseUrl("https://www.googleapis.com").build();
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return new GoogleAuthorizationCodeRequestUrl(clientId, redirectUri, SCOPES)
                .setState(state)
                .setAccessType("offline")
                .setApprovalPrompt("force") // force re-consent so a refresh token is issued even on reconnection
                .build();
    }

    @Override
    public GoogleTokenExchangeResult exchangeAuthorizationCode(String code) {
        try {
            TokenResponse tokenResponse = new GoogleAuthorizationCodeTokenRequest(
                    httpTransport, jsonFactory, clientId, clientSecret, code, redirectUri)
                    .execute();

            String refreshToken = tokenResponse.getRefreshToken();
            if (refreshToken == null) {
                throw new GoogleCalendarIntegrationException(
                        "Google não retornou um refresh token - verifique se o acesso offline foi concedido", null);
            }

            String googleEmail = fetchGoogleEmail(tokenResponse.getAccessToken());
            return new GoogleTokenExchangeResult(refreshToken, googleEmail);
        } catch (IOException ex) {
            throw new GoogleCalendarIntegrationException("Falha ao trocar o código de autorização com o Google", ex);
        }
    }

    @Override
    public String createEvent(String refreshToken, CreateCalendarEventCommand command) {
        try {
            Event event = new Event()
                    .setSummary(command.summary())
                    .setDescription(command.description())
                    .setStart(toEventDateTime(command.startsAt(), command.timezone()))
                    .setEnd(toEventDateTime(command.endsAt(), command.timezone()));

            Event created = buildCalendarClient(refreshToken).events().insert(CALENDAR_ID, event).execute();
            return created.getId();
        } catch (IOException ex) {
            throw new GoogleCalendarIntegrationException("Falha ao criar evento no Google Calendar", ex);
        }
    }

    @Override
    public void deleteEvent(String refreshToken, String googleEventId) {
        try {
            buildCalendarClient(refreshToken).events().delete(CALENDAR_ID, googleEventId).execute();
        } catch (IOException ex) {
            throw new GoogleCalendarIntegrationException("Falha ao cancelar evento no Google Calendar", ex);
        }
    }

    private Calendar buildCalendarClient(String refreshToken) {
        GoogleCredential credential = new GoogleCredential.Builder()
                .setTransport(httpTransport)
                .setJsonFactory(jsonFactory)
                .setClientSecrets(clientId, clientSecret)
                .build()
                .setRefreshToken(refreshToken);
        return new Calendar.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("SenaMed")
                .build();
    }

    private String fetchGoogleEmail(String accessToken) {
        try {
            Map<?, ?> userInfo = userInfoClient.get()
                    .uri("/oauth2/v2/userinfo")
                    .header("Authorization", "Bearer " + accessToken)
                    .retrieve()
                    .body(Map.class);
            Object email = userInfo != null ? userInfo.get("email") : null;
            return email != null ? email.toString() : null;
        } catch (RestClientException ex) {
            throw new GoogleCalendarIntegrationException("Falha ao obter o e-mail da conta Google conectada", ex);
        }
    }

    private static EventDateTime toEventDateTime(LocalDateTime localDateTime, String timezone) {
        long epochMillis = localDateTime.atZone(ZoneId.of(timezone)).toInstant().toEpochMilli();
        return new EventDateTime()
                .setDateTime(new DateTime(epochMillis))
                .setTimeZone(timezone);
    }
}
