package com.knowlink.api.bookings.services.interfaces;

public interface IBookingConfirmationTokenService {
    String generateToken();
    String hash(String rawToken);
    boolean matches(String rawToken, String hash);
}