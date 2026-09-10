package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;

import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.nio.charset.StandardCharsets;

@Service
public class BookingConfirmationTokenServiceImpl implements IBookingConfirmationTokenService {

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public String generateToken() {
        int value = secureRandom.nextInt(10_000); // 0000 a 9999
        return String.format("%04d", value);
    }

    @Override
    public String hash(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return bytesToHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no disponible", e);
        }
    }

    @Override
    public boolean matches(String rawToken, String hash) {
        // comparación en tiempo constante — no evita la fuerza bruta por sí sola
        // (para eso está el límite de intentos), pero evita filtrar por timing
        // cuánto del hash coincide
        return MessageDigest.isEqual(hash(rawToken).getBytes(StandardCharsets.UTF_8), hash.getBytes(StandardCharsets.UTF_8));
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) sb.append(String.format("%02x", b));
        return sb.toString();
    }
}