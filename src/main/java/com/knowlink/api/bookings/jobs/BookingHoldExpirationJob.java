package com.knowlink.api.bookings.jobs;

import com.knowlink.api.bookings.services.interfaces.IHoldService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class BookingHoldExpirationJob {

    private final IHoldService holdService;

    // El hold dura 15 minutos; un margen de hasta 1 minuto de retraso es aceptable.
    @Scheduled(fixedRate = 60_000)
    public void expireOverdueHolds() {
        try {
            holdService.expireOverdueHolds();
        } catch (Exception e) {
            // Excepción capturada para que un fallo puntual no detenga el scheduler.
            log.error("Error al expirar holds vencidos", e);
        }
    }
}
