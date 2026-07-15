package com.senamed.backend.googlecalendar;

public record GoogleTokenExchangeResult(String refreshToken, String googleEmail) {
}
