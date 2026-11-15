package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;
import com.knowlink.api.bookings.controllers.responses.BookingSlotResponse;
import com.knowlink.api.bookings.controllers.responses.BookingSlotUnavailableWindowResponse;
import com.knowlink.api.bookings.data.mappers.BookingCalendarMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.interfaces.IBookingCalendarService;
import com.knowlink.api.bookings.utils.ReservationWindowUtil;
import com.knowlink.api.shared.utils.PastTimeUtil;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingCalendarServiceImpl implements IBookingCalendarService {

    private final ITimeSlotRepository timeSlotRepository;
    private final IHoldRepository holdRepository;
    private final IBookingRepository bookingRepository;
    private final ITutorProfileValidationService tutorProfileValidationService;
    private final BookingCalendarMapper bookingCalendarMapper;
    private final Clock clock;

    @Override
    @Transactional(readOnly = true)
    public BookingCalendarResponse getCalendar(UUID tutorUserId, LocalDate from, LocalDate to) {
        TutorProfile tutorProfile = tutorProfileValidationService.findActiveTutorProfileOrThrowException(tutorUserId);
        UUID tutorProfileId = tutorProfile.getTutorProfileId();
        LocalDateTime now = LocalDateTime.now(clock);

        List<TimeSlot> timeSlots = timeSlotRepository.findInRange(
                tutorProfileId, from, to, now.toLocalDate(), now.toLocalTime());

        Map<UUID, List<Hold>> holdsBySlot = holdRepository.findActiveInRange(tutorProfileId, from, to)
                .stream()
                .collect(Collectors.groupingBy(h -> h.getTimeSlot().getTimeSlotId()));

        Map<UUID, List<Booking>> bookingsBySlot = bookingRepository
                .findActiveBookingsInRange(tutorUserId, from, to, BookingStatusGroups.ACTIVE)
                .stream()
                .collect(Collectors.groupingBy(b -> b.getTimeSlot().getTimeSlotId()));

        List<BookingSlotResponse> slots = timeSlots.stream()
                .map(slot -> toSlotResponse(slot, now, holdsBySlot, bookingsBySlot))
                .flatMap(Optional::stream)
                .toList();

        return new BookingCalendarResponse(tutorProfile.getMinNoticeMinutes(), slots);
    }

    private Optional<BookingSlotResponse> toSlotResponse(
            TimeSlot slot, LocalDateTime now,
            Map<UUID, List<Hold>> holdsBySlot, Map<UUID, List<Booking>> bookingsBySlot) {

        List<Hold> holds = holdsBySlot.getOrDefault(slot.getTimeSlotId(), List.of());
        List<Booking> bookings = bookingsBySlot.getOrDefault(slot.getTimeSlotId(), List.of());

        LocalTime effectiveStart = PastTimeUtil.effectiveStartTime(slot.getDate(), slot.getStartTime(), now);

        // Un bloque de hoy que ya empezó se muestra desde su inicio efectivo. Si lo que queda es menos de
        // una reserva mínima (1 h) no hay ninguna ventana que ofrecer: nadie lo bloqueó ni lo reservó,
        // simplemente ya no se puede reservar. Sin esta salida, allMatch() sobre una lista vacía daba true
        // y el tramo sobrante se mostraba como "Bloqueado por otro estudiante" (o "Reservado").
        List<LocalTime[]> windows = ReservationWindowUtil.getWindows(effectiveStart, slot.getEndTime());
        if (windows.isEmpty()) {
            return Optional.empty();
        }

        List<BookingSlotUnavailableWindowResponse> unavailableWindows = new ArrayList<>();
        holds.forEach(h -> unavailableWindows.add(
                bookingCalendarMapper.toWindowResponse(slot.getDate(), h.getStartTime(), h.getEndTime(), "BLOCKED")));
        bookings.forEach(b -> unavailableWindows.add(
                bookingCalendarMapper.toWindowResponse(slot.getDate(), b.getStartTime(), b.getEndTime(), "RESERVED")));

        boolean fullyCovered = windows.stream()
                .allMatch(window -> isWindowTaken(window[0], window[1], holds, bookings));

        String topLevelStatus;
        if (!fullyCovered) {
            topLevelStatus = "AVAILABLE";
        } else if (!bookings.isEmpty()) {
            topLevelStatus = "RESERVED";
        } else {
            topLevelStatus = "BLOCKED";
        }

        return Optional.of(
                bookingCalendarMapper.toSlotResponse(slot, effectiveStart, topLevelStatus, unavailableWindows));
    }

    private boolean isWindowTaken(LocalTime start, LocalTime end, List<Hold> holds, List<Booking> bookings) {
        boolean takenByHold = holds.stream()
                .anyMatch(h -> ReservationWindowUtil.overlaps(start, end, h.getStartTime(), h.getEndTime()));
        boolean takenByBooking = bookings.stream()
                .anyMatch(b -> ReservationWindowUtil.overlaps(start, end, b.getStartTime(), b.getEndTime()));
        return takenByHold || takenByBooking;
    }
}