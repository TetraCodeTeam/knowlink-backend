package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;
import com.knowlink.api.bookings.controllers.responses.BookingSlotResponse;
import com.knowlink.api.bookings.data.mappers.BookingCalendarMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.implementations.BookingCalendarServiceImpl;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCalendarServiceImplTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 11, 14);
    private static final UUID TUTOR_USER_ID = UUID.randomUUID();
    private static final UUID TUTOR_PROFILE_ID = UUID.randomUUID();

    @Mock private ITimeSlotRepository timeSlotRepository;
    @Mock private IHoldRepository holdRepository;
    @Mock private IBookingRepository bookingRepository;
    @Mock private ITutorProfileValidationService tutorProfileValidationService;

    @BeforeEach
    void setUpTutor() {
        TutorProfile tutorProfile = TutorProfile.builder()
                .tutorProfileId(TUTOR_PROFILE_ID)
                .minNoticeMinutes(60)
                .build();
        // lenient: varios tests reemplazan estos valores por defecto con sus propios holds o reservas.
        lenient().when(tutorProfileValidationService.findActiveTutorProfileOrThrowException(TUTOR_USER_ID))
                .thenReturn(tutorProfile);
        lenient().when(holdRepository.findActiveInRange(eq(TUTOR_PROFILE_ID), any(), any()))
                .thenReturn(List.of());
        lenient().when(bookingRepository.findActiveBookingsInRange(eq(TUTOR_USER_ID), any(), any(), any()))
                .thenReturn(List.of());
    }

    // ── Tramo sobrante de un horario que ya empezó ──────────────────────────────────────────

    @Test
    void leftoverShorterThanOneHourIsNotOfferedNorShownAsBlocked() {
        // El tutor ofrece hoy 08:30-12:00. A las 11:05 el horario se muestra desde las 11:30:
        // quedan 30 min, menos que una reserva mínima (1 h). Nadie lo bloqueó: no hay nada que ofrecer.
        TimeSlot slot = slot(TODAY, "08:30", "12:00");

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).isEmpty();
    }

    @Test
    void leftoverMovesOnWithTheClockAndStaysHidden() {
        // Pasadas las 11:30 el horario efectivo empieza a las 12:00 = su fin: tampoco debe aparecer.
        TimeSlot slot = slot(TODAY, "08:30", "12:00");

        List<BookingSlotResponse> slots = calendarAt("11:35", slot);

        assertThat(slots).isEmpty();
    }

    @Test
    void leftoverIsHiddenEvenIfTheBlockHasAnEarlierBooking() {
        // Una reserva anterior dentro del mismo bloque no convierte el tramo sobrante en "Reservado".
        TimeSlot slot = slot(TODAY, "08:30", "12:00");
        when(bookingRepository.findActiveBookingsInRange(eq(TUTOR_USER_ID), any(), any(), any()))
                .thenReturn(List.of(booking(slot, "09:00", "10:00")));

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).isEmpty();
    }

    @Test
    void blockWithRoomForAWindowIsStillOfferedFromItsEffectiveStart() {
        // A las 10:05 el horario se muestra desde las 10:30 y aún caben ventanas de 1 h.
        TimeSlot slot = slot(TODAY, "08:30", "12:00");

        List<BookingSlotResponse> slots = calendarAt("10:05", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("AVAILABLE");
        assertThat(slots.get(0).start()).isEqualTo(instant(TODAY, "10:30"));
        assertThat(slots.get(0).end()).isEqualTo(instant(TODAY, "12:00"));
    }

    @Test
    void lastWindowOfTheBlockIsStillOffered() {
        // A las 10:35 el horario arranca a las 11:00 y queda exactamente una ventana (11:00-12:00).
        TimeSlot slot = slot(TODAY, "08:30", "12:00");

        List<BookingSlotResponse> slots = calendarAt("10:35", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("AVAILABLE");
        assertThat(slots.get(0).start()).isEqualTo(instant(TODAY, "11:00"));
    }

    @Test
    void blockOfAnotherDayIsNotTrimmed() {
        TimeSlot slot = slot(TODAY.plusDays(1), "08:30", "18:30");

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("AVAILABLE");
        assertThat(slots.get(0).start()).isEqualTo(instant(TODAY.plusDays(1), "08:30"));
    }

    // ── Los bloqueos y reservas reales siguen mostrándose ───────────────────────────────────

    @Test
    void blockFullyCoveredByAnotherStudentsHoldIsBlocked() {
        TimeSlot slot = slot(TODAY.plusDays(1), "15:00", "16:00");
        when(holdRepository.findActiveInRange(eq(TUTOR_PROFILE_ID), any(), any()))
                .thenReturn(List.of(hold(slot, "15:00", "16:00")));

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("BLOCKED");
        assertThat(slots.get(0).unavailableWindows()).singleElement()
                .satisfies(window -> assertThat(window.status()).isEqualTo("BLOCKED"));
    }

    @Test
    void blockFullyCoveredByABookingIsReserved() {
        TimeSlot slot = slot(TODAY.plusDays(1), "15:00", "16:00");
        when(bookingRepository.findActiveBookingsInRange(eq(TUTOR_USER_ID), any(), any(), any()))
                .thenReturn(List.of(booking(slot, "15:00", "16:00")));

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("RESERVED");
    }

    @Test
    void blockPartiallyCoveredStaysAvailableAndListsTheTakenWindow() {
        TimeSlot slot = slot(TODAY.plusDays(1), "15:00", "18:00");
        when(bookingRepository.findActiveBookingsInRange(eq(TUTOR_USER_ID), any(), any(), any()))
                .thenReturn(List.of(booking(slot, "15:00", "16:00")));

        List<BookingSlotResponse> slots = calendarAt("11:05", slot);

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).status()).isEqualTo("AVAILABLE");
        assertThat(slots.get(0).unavailableWindows()).hasSize(1);
    }

    @Test
    void reportsTheTutorsMinimumNotice() {
        BookingCalendarResponse response = calendarResponseAt("11:05", slot(TODAY.plusDays(1), "09:00", "12:00"));

        assertThat(response.minimumNoticeMinutes()).isEqualTo(60);
    }

    // ── Helpers ─────────────────────────────────────────────────────────────────────────────

    private List<BookingSlotResponse> calendarAt(String nowTime, TimeSlot... slots) {
        return calendarResponseAt(nowTime, slots).slots();
    }

    private BookingCalendarResponse calendarResponseAt(String nowTime, TimeSlot... slots) {
        Clock clock = Clock.fixed(
                LocalDateTime.of(TODAY, LocalTime.parse(nowTime)).atZone(AppTimeZone.ZONE).toInstant(),
                AppTimeZone.ZONE);
        BookingCalendarServiceImpl service = new BookingCalendarServiceImpl(
                timeSlotRepository, holdRepository, bookingRepository,
                tutorProfileValidationService, new BookingCalendarMapper(), clock);
        when(timeSlotRepository.findInRange(eq(TUTOR_PROFILE_ID), any(), any(), any(), any()))
                .thenReturn(List.of(slots));

        return service.getCalendar(TUTOR_USER_ID, TODAY, TODAY.plusDays(6));
    }

    private static TimeSlot slot(LocalDate date, String start, String end) {
        return TimeSlot.builder()
                .timeSlotId(UUID.randomUUID())
                .tutorProfileId(TUTOR_PROFILE_ID)
                .date(date)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .status(SlotStatus.AVAILABLE)
                .build();
    }

    private static Hold hold(TimeSlot slot, String start, String end) {
        return Hold.builder()
                .holdId(UUID.randomUUID())
                .timeSlot(slot)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .build();
    }

    private static Booking booking(TimeSlot slot, String start, String end) {
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .timeSlot(slot)
                .startTime(LocalTime.parse(start))
                .endTime(LocalTime.parse(end))
                .build();
    }

    private static Instant instant(LocalDate date, String time) {
        return date.atTime(LocalTime.parse(time)).atZone(AppTimeZone.ZONE).toInstant();
    }
}
