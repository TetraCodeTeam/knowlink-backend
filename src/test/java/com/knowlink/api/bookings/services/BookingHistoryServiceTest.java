package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.data.enums.BookingHistoryCategory;
import com.knowlink.api.bookings.data.enums.BookingStatus;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.implementations.BookingServiceImpl;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.security.enums.Role;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingHistoryServiceTest {

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

    private final UUID studentId = UUID.randomUUID();

    @Test
    @DisplayName("RESERVED pide solo reservas futuras o vigentes")
    void reservedRequestsOnlyUpcoming() {
        when(bookingRepository.findHistoryByStudentAsc(
                any(), anyList(), eq(false), eq(false), eq(true), any(LocalDate.class),
                any(LocalTime.class), any()))
                .thenReturn(Page.empty());
        when(sessionClaimService.evaluateForListing(any(), anyList())).thenReturn(Map.of());

        service.getHistory(studentId, Role.STUDENT, BookingHistoryCategory.RESERVED, 0, 20);

        verify(bookingRepository).findHistoryByStudentAsc(
                eq(studentId),
                eq(List.of(BookingStatus.PENDING, BookingStatus.BOOKED)),
                eq(false),
                eq(false),
                eq(true),
                any(LocalDate.class),
                any(LocalTime.class),
                any());
    }

    @Test
    @DisplayName("NOT_CONFIRMED no se filtra por fecha y usa orden descendente")
    void notConfirmedDoesNotFilterByDate() {
        when(bookingRepository.findHistoryByStudentDesc(
                any(), anyList(), eq(false), eq(false), eq(false), any(LocalDate.class),
                any(LocalTime.class), any()))
                .thenReturn(Page.empty());
        when(sessionClaimService.evaluateForListing(any(), anyList())).thenReturn(Map.of());

        service.getHistory(studentId, Role.STUDENT, BookingHistoryCategory.NOT_CONFIRMED, 0, 20);

        verify(bookingRepository).findHistoryByStudentDesc(
                eq(studentId),
                eq(List.of(BookingStatus.NOT_CONFIRMED)),
                eq(false),
                eq(false),
                eq(false),
                any(LocalDate.class),
                any(LocalTime.class),
                any());
    }

    @Test
    @DisplayName("CANCELLED no se filtra por fecha")
    void cancelledDoesNotFilterByDate() {
        when(bookingRepository.findHistoryByStudentDesc(
                any(), anyList(), eq(false), eq(false), eq(false), any(LocalDate.class),
                any(LocalTime.class), any()))
                .thenReturn(Page.empty());
        when(sessionClaimService.evaluateForListing(any(), anyList())).thenReturn(Map.of());

        service.getHistory(studentId, Role.STUDENT, BookingHistoryCategory.CANCELLED, 0, 20);

        verify(bookingRepository).findHistoryByStudentDesc(
                eq(studentId),
                eq(List.of(BookingStatus.CANCELLED, BookingStatus.NOT_FULFILLED_BY_TUTOR,
                        BookingStatus.SESSION_NOT_HELD, BookingStatus.EXPIRED)),
                eq(false),
                eq(false),
                eq(false),
                any(LocalDate.class),
                any(LocalTime.class),
                any());
    }
}
