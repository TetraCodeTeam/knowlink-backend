package com.knowlink.api.bookings.services;

import com.knowlink.api.bookings.controllers.requests.CreateBookingRequest;
import com.knowlink.api.bookings.data.mappers.BookingMapper;
import com.knowlink.api.bookings.data.models.Hold;
import com.knowlink.api.bookings.events.BookingCreatedEvent;
import com.knowlink.api.bookings.repositories.IBookingRepository;
import com.knowlink.api.bookings.repositories.IHoldRepository;
import com.knowlink.api.bookings.services.implementations.BookingServiceImpl;
import com.knowlink.api.bookings.services.interfaces.IBookingConfirmationTokenService;
import com.knowlink.api.bookings.validations.IBookingValidationService;
import com.knowlink.api.claims.services.interfaces.ISessionClaimService;
import com.knowlink.api.events.services.BookingEventPublisher;
import com.knowlink.api.exceptions.custom_exceptions.BusinessConflictException;
import com.knowlink.api.payments.services.FundsLedgerService;
import com.knowlink.api.ratings.repositories.IRatingRepository;
import com.knowlink.api.shared.utils.AppTimeZone;
import com.knowlink.api.students.repositories.IStudentProfileRepository;
import com.knowlink.api.timeslot.data.enums.SlotStatus;
import com.knowlink.api.timeslot.data.models.TimeSlot;
import com.knowlink.api.tutors.data.enums.CompensationType;
import com.knowlink.api.tutors.data.enums.Modality;
import com.knowlink.api.tutors.data.enums.TutorSubjectStatus;
import com.knowlink.api.tutors.data.models.TutorSubject;
import com.knowlink.api.tutors.repositories.ITutorSubjectRepository;
import com.knowlink.api.users.data.models.User;
import com.knowlink.api.users.services.interfaces.IUserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceImplInactiveSubjectTest {

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

    @Test
    @DisplayName("createBooking rechaza una materia inactiva con TUTOR_SUBJECT_INACTIVE")
    void createBooking_inactiveSubject_throwsConflict() {
        UUID studentId = UUID.randomUUID();
        UUID timeSlotId = UUID.randomUUID();
        UUID tutorSubjectId = UUID.randomUUID();
        LocalDate date = LocalDate.now().plusDays(1);
        LocalTime startTime = LocalTime.of(10, 0);
        LocalTime endTime = LocalTime.of(11, 0);

        User student = User.builder().userId(studentId).fullName("Alumno Test").build();
        when(userService.findByIdOrThrowException(studentId)).thenReturn(student);

        TimeSlot timeSlot = TimeSlot.builder()
                .timeSlotId(timeSlotId)
                .date(date)
                .startTime(startTime)
                .endTime(endTime)
                .status(SlotStatus.AVAILABLE)
                .tutorProfileId(UUID.randomUUID())
                .build();

        Hold hold = Hold.builder()
                .timeSlot(timeSlot)
                .startTime(startTime)
                .endTime(endTime)
                .expiresAt(LocalDateTime.now(AppTimeZone.ZONE).plusMinutes(10))
                .build();

        when(holdRepository.findActiveHoldForWindowForUpdate(eq(studentId), eq(timeSlotId), any(), any()))
                .thenReturn(Optional.of(hold));

        TutorSubject inactiveSubject = TutorSubject.builder()
                .tutorSubjectId(tutorSubjectId)
                .compensationType(CompensationType.PAID)
                .tutorSubjectStatus(TutorSubjectStatus.ACTIVE)
                .modality(Modality.VIRTUAL)
                .active(false)
                .build();
        when(tutorSubjectRepository.findByIdForUpdate(tutorSubjectId))
                .thenReturn(Optional.of(inactiveSubject));

        Instant start = date.atTime(startTime).atZone(AppTimeZone.ZONE).toInstant();
        Instant end = date.atTime(endTime).atZone(AppTimeZone.ZONE).toInstant();
        CreateBookingRequest request = new CreateBookingRequest(
                timeSlotId + "__" + startTime, tutorSubjectId, "Tema", Modality.VIRTUAL, start, end);

        assertThatThrownBy(() -> service.createBooking(studentId, request))
                .isInstanceOfSatisfying(BusinessConflictException.class,
                        ex -> assertThat(ex.getErrorCode()).isEqualTo("TUTOR_SUBJECT_INACTIVE"));

        verify(bookingRepository, never()).save(any());
        verify(applicationEventPublisher, never()).publishEvent(any(BookingCreatedEvent.class));
    }
}
