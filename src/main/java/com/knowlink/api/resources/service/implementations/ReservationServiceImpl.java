package com.knowlink.api.resources.service.implementations;

import com.knowlink.api.resources.service.interfaces.IReservationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@Slf4j
@Profile({"test", "development"})
public class ReservationServiceImpl implements IReservationService {

    // TODO(US-26): Implement real reservation check using BookingRepository
    @Override
    public boolean hasActiveReservation(UUID userId, UUID subjectId) {
        log.warn("ReservationService stub called - TODO(US-26) implement real check");
        return true;
    }

    // TODO(US-26): Implement real reservation check using BookingRepository
    @Override
    public boolean hasAnyReservationForSubject(UUID userId, UUID subjectId) {
        log.warn("ReservationService.hasAnyReservationForSubject stub called - TODO(US-26) implement real check");
        return true;
    }

    // TODO(US-26): Implement real reservation check using BookingRepository
    @Override
    public boolean hasReservationWithTutorForSubject(UUID userId, UUID tutorId, UUID subjectId) {
        log.warn("ReservationService.hasReservationWithTutorForSubject stub called - TODO(US-26) implement real check");
        return true;
    }
}
