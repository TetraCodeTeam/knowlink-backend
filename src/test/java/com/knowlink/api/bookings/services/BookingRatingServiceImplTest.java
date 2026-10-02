package com.knowlink.api.bookings.services;

import com.knowlink.api.ratings.controllers.requests.CreateRatingRequest;
import com.knowlink.api.ratings.controllers.responses.RatingResponse;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.ratings.data.mappers.RatingMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.ratings.services.implementations.RatingServiceImpl;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.exceptions.custom_exceptions.DuplicateResourceException;
import com.knowlink.api.exceptions.custom_exceptions.ValidationException;
import com.knowlink.api.ratings.data.models.Rating;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.ratings.services.interfaces.IRatingReputationService;
import com.knowlink.api.users.data.models.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Clock;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingRatingServiceImplTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 2, 11, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private IBookingValidationService bookingValidationService;
    @Mock
    private IRatingReputationService ratingReputationService;
    private RatingServiceImpl bookingRatingService;

    @BeforeEach
    void setUp() {
        bookingRatingService = new RatingServiceImpl(
                bookingRepository,
                ratingRepository,
                ratingReputationService,
                bookingValidationService,
                new RatingMapper(),
                CLOCK);
    }

    @Test
    void firstRatingIsSavedAsHidden() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, NOW.minusHours(1));
        prepareBooking(bookingId, booking, List.of());

        RatingResponse response = bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(5, "Muy clara la clase"));

        ArgumentCaptor<Rating> savedRating = ArgumentCaptor.forClass(Rating.class);
        verify(ratingRepository).save(savedRating.capture());
        assertFalse(savedRating.getValue().isVisible());
        assertEquals(tutor, savedRating.getValue().getRatedUser());
        assertFalse(response.visible());
        assertTrue(response.message().contains("definitiva"));
    }

    @Test
    void secondRatingMakesBothRatingsVisible() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, NOW.minusHours(1));
        Rating firstRating = Rating.builder()
                .booking(booking)
                .raterUser(student)
                .ratedUser(tutor)
                .score(4)
                .ratingDate(NOW.minusMinutes(20))
                .visible(false)
                .build();
        prepareBooking(bookingId, booking, List.of(firstRating));

        RatingResponse response = bookingRatingService.submitRating(
                tutor.getUserId(), bookingId, new CreateRatingRequest(5, null));

        assertTrue(response.visible());
        assertTrue(firstRating.isVisible());
        verify(ratingRepository).saveAll(anyList());
        verify(ratingReputationService).refreshTutorAverages(anyList());
    }

    @Test
    void ratingAfterTwentyFourHoursIsImmediatelyVisible() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, NOW.minusHours(25));
        prepareBooking(bookingId, booking, List.of());

        RatingResponse response = bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(3, null));

        assertTrue(response.visible());
        verify(ratingRepository).saveAll(anyList());
        verify(ratingReputationService).refreshTutorAverages(anyList());
    }

    @Test
    void alreadySubmittedRatingCannotBeModified() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, NOW.minusHours(1));
        Rating existingRating = Rating.builder().raterUser(student).build();
        prepareBooking(bookingId, booking, List.of(existingRating));

        DuplicateResourceException exception = assertThrows(DuplicateResourceException.class,
            () -> bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(1, "Cambio")));
        assertEquals("RATING_ALREADY_SUBMITTED", exception.getErrorCode());

        verify(ratingRepository, never()).save(any(Rating.class));
        verify(ratingRepository, never()).saveAll(anyList());
    }

    @Test
    void ratingAtExactTwentyFourHourBoundaryIsVisible() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, NOW.minusHours(24));
        prepareBooking(bookingId, booking, List.of());

        RatingResponse response = bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(4, null));

        assertTrue(response.visible());
    }

    @Test
    void missingConfirmedAtUsesSessionEndAsDeadlineStart() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = completedBooking(student, tutor, null);
        booking.setSessionDate(NOW.minusDays(2).toLocalDate());
        prepareBooking(bookingId, booking, List.of());

        RatingResponse response = bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(4, null));

        assertTrue(response.visible());
    }

    @Test
    void cancelledBookingCannotBeRated() {
        UUID bookingId = UUID.randomUUID();
        User student = user();
        User tutor = user();
        Booking booking = Booking.builder()
                .student(student)
                .tutor(tutor)
                .bookingStatus(BookingStatus.CANCELLED)
                .build();
        prepareBooking(bookingId, booking, List.of());

        assertThrows(ValidationException.class, () -> bookingRatingService.submitRating(
                student.getUserId(), bookingId, new CreateRatingRequest(5, null)));

        verify(ratingRepository, never()).save(any(Rating.class));
        verify(ratingRepository, never()).saveAll(anyList());
    }

    private void prepareBooking(UUID bookingId, Booking booking, List<Rating> existingRatings) {
        when(bookingRepository.findByIdForUpdate(bookingId)).thenReturn(Optional.of(booking));
        if (booking.getBookingStatus() == BookingStatus.COMPLETED) {
            when(ratingRepository.findByBookingBookingId(bookingId)).thenReturn(existingRatings);
        }
    }

    private Booking completedBooking(User student, User tutor, LocalDateTime confirmedAt) {
        return Booking.builder()
                .student(student)
                .tutor(tutor)
                .bookingStatus(BookingStatus.COMPLETED)
                .confirmedAt(confirmedAt)
                .sessionDate(LocalDate.now())
                .endTime(LocalTime.NOON)
                .build();
    }

    private User user() {
        return User.builder().userId(UUID.randomUUID()).build();
    }
}