package com.knowlink.api.bookings.services.implementations;

import com.knowlink.api.bookings.controllers.responses.BookingCalendarResponse;
import com.knowlink.api.bookings.controllers.responses.BookingSlotResponse;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingCalendarMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.timeslot.repositories.ITimeSlotRepository;
import com.knowlink.api.tutors.availability.data.enums.BookingStatusGroups;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.validations.ITutorProfileValidationService;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingCalendarServiceImplTest {

    @Mock
    private ITimeSlotRepository timeSlotRepository;
    @Mock
    private IHoldRepository holdRepository;
    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private ITutorProfileValidationService tutorProfileValidationService;

    private BookingCalendarServiceImpl bookingCalendarService;

    private final UUID tutorUserId = UUID.randomUUID();
    private final UUID tutorProfileId = UUID.randomUUID();
    private final LocalDate date = LocalDate.now().plusWeeks(1);

    @BeforeEach
    void setUp() {
        bookingCalendarService = new BookingCalendarServiceImpl(
                timeSlotRepository,
                holdRepository,
                bookingRepository,
                tutorProfileValidationService,
                new BookingCalendarMapper());
    }

    @Test
    @DisplayName("CP-CAL.01 - una reserva COMPLETED mantiene el slot completo como RESERVED")
    void getCalendar_completedBookingCoversSlot_marksSlotReserved() {
        TimeSlot slot = buildSlot();
        Booking completed = buildBooking(slot, LocalTime.of(14, 0), LocalTime.of(16, 0));
        stubCalendar(slot, List.of(completed));

        BookingCalendarResponse result = bookingCalendarService.getCalendar(tutorUserId, date, date);

        assertThat(result.slots()).hasSize(1);
        BookingSlotResponse slotResponse = result.slots().get(0);
        assertThat(slotResponse.status()).isEqualTo("RESERVED");
        assertThat(slotResponse.unavailableWindows()).hasSize(1);
        assertThat(slotResponse.unavailableWindows().get(0).status()).isEqualTo("RESERVED");
    }

    @Test
    @DisplayName("CP-CAL.02 - una reserva COMPLETED parcial expone la ventana ocupada como RESERVED")
    void getCalendar_completedBookingCoversPartOfSlot_exposesReservedWindow() {
        TimeSlot slot = buildSlot();
        Booking completed = buildBooking(slot, LocalTime.of(14, 0), LocalTime.of(15, 0));
        stubCalendar(slot, List.of(completed));

        BookingCalendarResponse result = bookingCalendarService.getCalendar(tutorUserId, date, date);

        BookingSlotResponse slotResponse = result.slots().get(0);
        // The remaining window is still free, but the completed class must not be
        // offered again: its window stays visible as RESERVED.
        assertThat(slotResponse.status()).isEqualTo("AVAILABLE");
        assertThat(slotResponse.unavailableWindows()).hasSize(1);
        assertThat(slotResponse.unavailableWindows().get(0).status()).isEqualTo("RESERVED");
    }

    private void stubCalendar(TimeSlot slot, List<Booking> bookings) {
        TutorProfile tutorProfile = TutorProfile.builder()
                .tutorProfileId(tutorProfileId)
                .user(User.builder().userId(tutorUserId).fullName("Ana García").build())
                .minNoticeMinutes(0)
                .build();
        when(tutorProfileValidationService.findActiveTutorProfileOrThrowException(tutorUserId))
                .thenReturn(tutorProfile);

        when(timeSlotRepository.findInRange(
                        eq(tutorProfileId), eq(date), eq(date), any(LocalDate.class), any(LocalTime.class)))
                .thenReturn(List.of(slot));

        when(holdRepository.findActiveInRange(tutorProfileId, date, date)).thenReturn(List.of());

        when(bookingRepository.findActiveBookingsInRange(tutorUserId, date, date, BookingStatusGroups.OCCUPYING))
                .thenReturn(bookings);
    }

    private TimeSlot buildSlot() {
        return TimeSlot.builder()
                .timeSlotId(UUID.randomUUID())
                .date(date)
                .startTime(LocalTime.of(14, 0))
                .endTime(LocalTime.of(16, 0))
                .status(SlotStatus.OCCUPIED)
                .tutorProfileId(tutorProfileId)
                .build();
    }

    private Booking buildBooking(TimeSlot slot, LocalTime start, LocalTime end) {
        return Booking.builder()
                .bookingId(UUID.randomUUID())
                .sessionDate(slot.getDate())
                .startTime(start)
                .endTime(end)
                .bookingStatus(BookingStatus.COMPLETED)
                .timeSlot(slot)
                .build();
    }
}