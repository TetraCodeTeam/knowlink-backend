package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.exceptions.custom_exceptions.ResourceNotFoundException;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.bookings.controllers.requests.CreateHoldRequest;
import com.knowlink.api.bookings.controllers.responses.HoldResponse;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IHoldService;
import com.knowlink.api.bookings.utils.BookingConstants;
import com.knowlink.api.bookings.validations.IHoldValidationService;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class HoldServiceImpl implements IHoldService {

    private final IHoldRepository holdRepository;
    private final ITimeSlotRepository timeSlotRepository;
    private final IHoldValidationService holdValidationService;
    private final IUserService userService;
    private final BookingEventPublisher eventPublisher;
    private final ITutorProfileValidationService tutorProfileValidationService;

    @Override
    @Transactional
    public HoldResponse createHold(UUID tutorUserId, UUID studentUserId, CreateHoldRequest request) {
        User student = userService.findByIdOrThrowException(studentUserId);

        // Resolvemos el tutor primero (lectura simple, sin lock) para saber contra qué
        // tutorProfileId validar — recién después tomamos el lock de escritura, y solo
        // si el slot realmente pertenece a este tutor.
        TutorProfile tutorProfile = tutorProfileValidationService.findTutorProfileOrThrowException(tutorUserId);

        TimeSlot timeSlot = timeSlotRepository
                .findByIdAndTutorProfileIdForUpdate(request.slotId(), tutorProfile.getTutorProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "TIME_SLOT_NOT_FOUND",
                        "El horario seleccionado ya no está disponible.",
                        "TimeSlot not found or doesn't belong to tutorProfile "
                                + tutorProfile.getTutorProfileId() + ": " + request.slotId()));

        LocalTime startTime = request.start().atZone(AppTimeZone.ZONE).toLocalTime();
        LocalTime endTime = request.end().atZone(AppTimeZone.ZONE).toLocalTime();

        holdValidationService.validateExactDuration(startTime, endTime);
        holdValidationService.validateTimeWithinSlot(timeSlot, startTime, endTime);
        holdValidationService.validateMinNotice(tutorProfile, timeSlot, startTime);
        holdValidationService.validateNoOverlap(timeSlot.getTimeSlotId(), startTime, endTime);
        holdValidationService.validateSingleActiveHold(student);

        Hold hold = Hold.builder()
                .timeSlot(timeSlot)
                .student(student)
                .startTime(startTime)
                .endTime(endTime)
                .expiresAt(LocalDateTime.now().plusMinutes(BookingConstants.HOLD_MINUTES))
                .build();

        holdRepository.save(hold);

        eventPublisher.publish(tutorProfile.getTutorProfileId(),
                timeSlot.getTimeSlotId(), "BLOCKED", request.start(), request.end());

        return new HoldResponse(hold.getHoldId(), timeSlot.getTimeSlotId(), "BLOCKED", hold.getExpiresAt());
    }

    @Override
    @Transactional
    public void releaseHold(UUID studentUserId, CreateHoldRequest request) {
        LocalTime startTime = request.start().atZone(AppTimeZone.ZONE).toLocalTime();
        LocalTime endTime = request.end().atZone(AppTimeZone.ZONE).toLocalTime();

        // Idempotente: si no existe (ya liberado, ya expirado, ya convertido a
        // Booking),
        // no hace nada — no es un error.
        holdRepository.findActiveHoldForWindow(studentUserId, request.slotId(), startTime, endTime)
                .ifPresent(hold -> {
                    UUID tutorProfileId = hold.getTimeSlot().getTutorProfileId();
                    holdRepository.delete(hold);
                    eventPublisher.publish(tutorProfileId, request.slotId(), "AVAILABLE", request.start(),
                            request.end());
                });
    }

    @Override
    @Transactional
    public void expireOverdueHolds() {
        List<Hold> expired = holdRepository.findExpired(LocalDateTime.now());

        for (Hold expiredHold : expired) {
            // Vuelve a leer con lock, por si otra transacción (createBooking) ya la está
            // consumiendo en este mismo instante — evita expirar un hold que está a
            // punto de convertirse en reserva.
            Hold locked = holdRepository.findByIdForUpdate(expiredHold.getHoldId()).orElse(null);
            if (locked == null) {
                continue; // ya fue consumida por createBooking, nada que hacer
            }

            UUID tutorProfileId = locked.getTimeSlot().getTutorProfileId();
            UUID timeSlotId = locked.getTimeSlot().getTimeSlotId();
            LocalDate date = locked.getTimeSlot().getDate();
            LocalTime start = locked.getStartTime();
            LocalTime end = locked.getEndTime();

            holdRepository.delete(locked);

            eventPublisher.publish(tutorProfileId, timeSlotId, "AVAILABLE",
                    start.atDate(date).atZone(AppTimeZone.ZONE).toInstant(),
                    end.atDate(date).atZone(AppTimeZone.ZONE).toInstant());
        }
    }
}
