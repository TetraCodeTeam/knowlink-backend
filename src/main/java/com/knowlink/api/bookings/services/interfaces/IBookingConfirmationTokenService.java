package com.knowlink.api.bookings.services.interfaces;

public interface IBookingConfirmationTokenService {
    String generateToken();
    String encrypt(String rawToken);
    String decrypt(String encryptedToken);
    boolean matches(String rawToken, String encryptedToken);
}