package com.senamed.backend.googlecalendar;

/**
 * Thin abstraction over Google's OAuth2 + Calendar REST APIs - kept as an interface (rather than a
 * concrete class directly injected) so tests can {@code @MockitoBean} it and never make a real
 * network call, mirroring {@code MercadoPagoClient}. Unlike Mercado Pago (one app-wide access
 * token), a Google Calendar credential is per-doctor, so the refresh token travels as a method
 * parameter on every call rather than being injected statically.
 */
public interface GoogleCalendarClient {

    /** Builds the URL to redirect a doctor's browser to for Google's consent screen. */
    String buildAuthorizationUrl(String state);

    /** Exchanges a one-time authorization code (from the OAuth callback) for a refresh token + the connected account's email. */
    GoogleTokenExchangeResult exchangeAuthorizationCode(String code);

    /** Creates an event on the doctor's primary calendar, returning Google's event id. */
    String createEvent(String refreshToken, CreateCalendarEventCommand command);

    /** Deletes a previously created event. */
    void deleteEvent(String refreshToken, String googleEventId);
}
