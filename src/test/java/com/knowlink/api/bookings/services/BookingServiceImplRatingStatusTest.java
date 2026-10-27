package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Booking;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.implementations.BookingServiceImpl;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.ratings.data.enums.RatingStatus;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.data.models.Subject;
import com.knowlink.api.tutors.data.models.TutorProfile;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplRatingStatusTest {

    private static final ZoneId ZONE = ZoneId.of("America/Argentina/Cordoba");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 17, 0);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZONE).toInstant(), ZONE);

    @Mock
    private IBookingRepository bookingRepository;
    @Mock
    private ITutorSubjectRepository tutorSubjectRepository;
    @Mock
    private IHoldRepository holdRepository;
    @Mock
    private IBookingValidationService bookingValidationService;
    @Mock
    private IUserService userService;
    @Mock
    private BookingMapper bookingMapper;
    @Mock
    private BookingEventPublisher bookingEventPublisher;
    @Mock
    private ApplicationEventPublisher applicationEventPublisher;
    @Mock
    private IBookingConfirmationTokenService confirmationTokenService;
    @Mock
    private IStudentProfileRepository studentProfileRepository;
    @Mock
    private ISessionClaimService sessionClaimService;
    @Mock
    private FundsLedgerService fundsLedgerService;
    @Mock
    private IRatingRepository ratingRepository;
    @Mock
    private Clock clock;

    @InjectMocks
    private BookingServiceImpl service;

    private UUID studentId;
    private UUID bookingId;
    private Booking booking;

    @BeforeEach
    void setUp() {
        lenient().when(clock.instant()).thenReturn(NOW.atZone(ZONE).toInstant());
        lenient().when(clock.getZone()).thenReturn(ZONE);
        studentId = UUID.randomUUID();
        bookingId = UUID.randomUUID();
        User student = User.builder().userId(studentId).fullName("Alumno").build();
        User tutor = User.builder().userId(UUID.randomUUID()).fullName("Tutor").build();
        TutorProfile tutorProfile = TutorProfile.builder().profilePictureUrl("picture").build();
        TutorSubject tutorSubject = TutorSubject.builder()
                .subject(Subject.builder().name("Sistemas").build())
                .tutorProfile(tutorProfile)
                .build();
        booking = Booking.builder()
                .bookingId(bookingId)
                .student(student)
                .tutor(tutor)
                .tutorSubject(tutorSubject)
                .bookingStatus(BookingStatus.COMPLETED)
                .sessionDate(LocalDate.of(2026, 10, 20))
                .startTime(LocalTime.of(15, 0))
                .endTime(LocalTime.of(16, 0))
                .confirmedAt(LocalDateTime.of(2026, 10, 20, 15, 30))
                .build();
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
    }

    @Test
    @DisplayName("Detalle - reserva no COMPLETED: NOT_AVAILABLE sin plazo")
    void detail_notCompleted_isNotAvailable() {
        booking.setBookingStatus(BookingStatus.BOOKED);

        service.getDetail(studentId, bookingId);

        verify(bookingMapper).toDetail(eq(booking), eq(studentId), any(), eq(RatingStatus.NOT_AVAILABLE),
                eq(null));
    }

    @Test
    @DisplayName("Detalle - el usuario ya calificó: SUBMITTED")
    void detail_alreadyRated_isSubmitted() {
        when(ratingRepository.existsByBooking_BookingIdAndRaterUser_UserId(bookingId, studentId)).thenReturn(true);

        service.getDetail(studentId, bookingId);

        verify(bookingMapper).toDetail(eq(booking), eq(studentId), any(), eq(RatingStatus.SUBMITTED),
                eq(LocalDateTime.of(2026, 10, 21, 16, 0)));
    }

    @Test
    @DisplayName("Detalle - dentro del plazo: PENDING")
    void detail_withinWindow_isPending() {
        when(ratingRepository.existsByBooking_BookingIdAndRaterUser_UserId(bookingId, studentId)).thenReturn(false);

        service.getDetail(studentId, bookingId);

        verify(bookingMapper).toDetail(eq(booking), eq(studentId), any(), eq(RatingStatus.PENDING),
                eq(LocalDateTime.of(2026, 10, 21, 16, 0)));
    }

    @Test
    @DisplayName("Detalle - plazo vencido: EXPIRED")
    void detail_afterDeadline_isExpired() {
        booking.setSessionDate(LocalDate.of(2026, 10, 18));
        booking.setConfirmedAt(LocalDateTime.of(2026, 10, 18, 16, 5));
        when(ratingRepository.existsByBooking_BookingIdAndRaterUser_UserId(bookingId, studentId)).thenReturn(false);

        service.getDetail(studentId, bookingId);

        verify(bookingMapper).toDetail(eq(booking), eq(studentId), any(), eq(RatingStatus.EXPIRED),
                eq(LocalDateTime.of(2026, 10, 19, 16, 5)));
    }
}